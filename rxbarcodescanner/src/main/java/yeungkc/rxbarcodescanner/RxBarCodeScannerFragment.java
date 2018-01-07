package yeungkc.rxbarcodescanner;

import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Flash;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.otaliastudios.cameraview.Size;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

public class RxBarCodeScannerFragment extends Fragment {

    private static final RxBarCodeScannerLogger LOG = RxBarCodeScannerLogger.create(Decode.class.getSimpleName());

    private static final String KEY_DECODE_FORMATS = "KEY_DECODE_FORMATS";
    private static final int POST_FOCUS_DELAY = 500;

    private CameraView mCameraView;
    private FrameProcessor mFrameProcessor;
    private Flowable<Pair<Result, ReaderException>> mResultFlowable;
    private OnCodeScannerListener mOnCodeScannerListener;
    private Subject<Object> mFocusSubject = PublishSubject.<Object>create();
    private Disposable mFocusDisposable;
    private int mPostFocusDelay;
    private boolean mIsContinuousFocus;
    private AmbientLightManager mAmbientLightManager;
    private Integer mFocusX;
    private Integer mFocusY;

    public static RxBarCodeScannerFragment newInstance(Collection<BarcodeFormat> decodeFormats) {

        Bundle args = new Bundle();

        RxBarCodeScannerFragment fragment = new RxBarCodeScannerFragment();
        ArrayList<BarcodeFormat> barcodeFormats = new ArrayList<>(decodeFormats);
        args.putSerializable(KEY_DECODE_FORMATS, barcodeFormats);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Collection<BarcodeFormat> decodeFormats;

        Serializable serializable = getArguments().getSerializable(KEY_DECODE_FORMATS);
        if (serializable != null) {
            decodeFormats = (Collection<BarcodeFormat>) serializable;
        } else {
            decodeFormats = DecodeFormatManager.CODE_128_AND_QR_CODE_FORMATS;
        }

        final Decode decode = new Decode(decodeFormats);

        mResultFlowable = Flowable.create(new FlowableOnSubscribe<Frame>() {
            @Override
            public void subscribe(final FlowableEmitter<Frame> e) throws Exception {
                setFrameProcessor(new FrameProcessor() {
                    @Override
                    public void process(@NonNull Frame frame) {
                        if (frame.getData() != null) e.onNext(frame);
                    }
                });
            }
        }, BackpressureStrategy.DROP)
                .map(new Function<Frame, Pair<Result, ReaderException>>() {
                    @Override
                    public Pair<Result, ReaderException> apply(Frame frame) throws Exception {
                        LOG.i("rotation", frame.getRotation() + " : " + frame.getSize());
                        return decode.decode(frame.getData(), frame.getSize().getWidth(), frame.getSize().getHeight(), frame.getRotation());
                    }
                });

        if (mOnCodeScannerListener != null) {
            mOnCodeScannerListener.onResultFlowableReady(mResultFlowable);
        }

        mAmbientLightManager = new AmbientLightManager(getContext());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return new CameraView(container.getContext());
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCameraView = (CameraView) view;
        mCameraView.setPlaySounds(false);

        mCameraView.addCameraListener(new CameraListener() {
            @Override
            public void onCameraOpened(CameraOptions options) {
                super.onCameraOpened(options);
                if (mOnCodeScannerListener != null) {
                    mOnCodeScannerListener.onCameraOpened();
                    mOnCodeScannerListener.onOpenTorch(getTorch());
                }
                if (mFocusSubject != null && mIsContinuousFocus) mFocusSubject.onNext(FocusNotification.INSTANCE);
            }

            @Override
            public void onFocusEnd(boolean successful, PointF point) {
                super.onFocusEnd(successful, point);
                if (mFocusSubject != null && mIsContinuousFocus) mFocusSubject.onNext(FocusNotification.INSTANCE);
            }
        });

        mFocusSubject = PublishSubject.<Object>create();

        mPostFocusDelay = POST_FOCUS_DELAY;
        mFocusDisposable = mFocusSubject.delay(mPostFocusDelay, TimeUnit.MILLISECONDS)
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object o) throws Exception {
                        if (mFocusX != null && mFocusY != null) {
                            startAutoFocus(mFocusX, mFocusY);
                            return;
                        }
                        startAutoFocus(mCameraView.getWidth() / 2, mCameraView.getHeight() / 2);
                    }
                });

        if (mFrameProcessor != null) mCameraView.addFrameProcessor(mFrameProcessor);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAmbientLightManager.start(new AmbientLightManager.AmbientLightListener() {
            @Override
            public void needOpenTorch(boolean needOpenTorch) {
                if (mOnCodeScannerListener != null) mOnCodeScannerListener.needOpenTorch(needOpenTorch);
            }
        });
        mCameraView.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        mAmbientLightManager.stop();
        mCameraView.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFocusDisposable != null && !mFocusDisposable.isDisposed()) mFocusDisposable.dispose();
        mCameraView.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean valid = true;
        for (int grantResult : grantResults) {
            valid = valid && grantResult == PackageManager.PERMISSION_GRANTED;
        }
        if (valid && !mCameraView.isStarted()) {
            mCameraView.start();
        }
    }

    private void setFrameProcessor(FrameProcessor frameProcessor) {
        mFrameProcessor = frameProcessor;
    }

    public Flowable<Pair<Result, ReaderException>> getResultFlowable() {
        return mResultFlowable;
    }

    public void setOnCodeScannerListener(OnCodeScannerListener onCodeScannerListener) {
        mOnCodeScannerListener = onCodeScannerListener;

        Flowable<Pair<Result, ReaderException>> resultFlowable = getResultFlowable();
        if (resultFlowable == null) return;

        mOnCodeScannerListener.onResultFlowableReady(resultFlowable);
    }

    public void startAutoFocus(int x, int y) {
        mCameraView.startAutoFocus(x, y);
    }

    public void setTorch(boolean torch) {
        if (mCameraView == null) return;
        mCameraView.setFlash(torch ? Flash.TORCH : Flash.OFF);
        mOnCodeScannerListener.onOpenTorch(getTorch());
    }

    public boolean getTorch() {
        return mCameraView != null && mCameraView.getFlash() == Flash.TORCH;
    }

    public void setPostFocusDelay(int postFocusDelay) {
        mPostFocusDelay = postFocusDelay;
    }

    public int getPostFocusDelay() {
        return mPostFocusDelay;
    }

    public void setContinuousFocus(boolean continuousFocus) {
        mIsContinuousFocus = continuousFocus;

        if (mIsContinuousFocus && mCameraView != null) {
            startAutoFocus(mCameraView.getWidth() / 2, mCameraView.getHeight() / 2);
        }
    }

    public boolean isContinuousFocus() {
        return mIsContinuousFocus;
    }

    public Size previewSize() {
        if (mCameraView == null) return null;
        return mCameraView.getPreviewSize();
    }

    public void setFocusLocation(Integer focusX, Integer focusY) {
        mFocusX = focusX;
        mFocusY = focusY;
    }

    public interface OnCodeScannerListener {

        void onResultFlowableReady(Flowable<Pair<Result, ReaderException>> flowable);

        void onOpenTorch(boolean torch);

        void needOpenTorch(boolean needOpenTorch);

        void onCameraOpened();
    }
}
