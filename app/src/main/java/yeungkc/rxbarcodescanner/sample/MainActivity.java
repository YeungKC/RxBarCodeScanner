package yeungkc.rxbarcodescanner.sample;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.widget.Button;

import com.google.zxing.ReaderException;
import com.google.zxing.Result;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import yeungkc.rxbarcodescanner.DecodeFormatManager;
import yeungkc.rxbarcodescanner.RxBarCodeScanner;
import yeungkc.rxbarcodescanner.RxBarCodeScannerFragment;
import yeungkc.rxbarcodescanner.RxBarCodeScannerLogger;
import yeungkc.rxbarcodescanner.view.ScanBoxView;

public class MainActivity extends AppCompatActivity {

    private FlowableProcessor<String> mScanResult = PublishProcessor.<String>create().toSerialized();
    private Disposable mScanResultSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RxBarCodeScannerLogger.setLogLevel(RxBarCodeScannerLogger.LEVEL_VERBOSE);

        final ScanBoxView scanBoxView = findViewById(R.id.scan_box_view);

        final Button button = findViewById(R.id.button_torch);

        final RxBarCodeScannerFragment rxBarCodeScannerFragment = RxBarCodeScanner.scanFromCamera(
                getSupportFragmentManager(),
                R.id.fragment,
                DecodeFormatManager.CODE_128_AND_QR_CODE_FORMATS
        );

        if (rxBarCodeScannerFragment != null) {
            rxBarCodeScannerFragment.setOnCodeScannerListener(new RxBarCodeScannerFragment.OnCodeScannerListener() {

                @Override
                public void onResultFlowableReady(Flowable<Pair<Result, ReaderException>> flowable) {
                    flowable.observeOn(Schedulers.computation())
                            .filter(new Predicate<Pair<Result, ReaderException>>() {
                                @Override
                                public boolean test(Pair<Result, ReaderException> resultReaderExceptionPair) throws Exception {
                                    return resultReaderExceptionPair.first != null &&
                                            !TextUtils.isEmpty(resultReaderExceptionPair.first.getText());
                                }
                            })
                            .map(new Function<Pair<Result, ReaderException>, String>() {
                                @Override
                                public String apply(Pair<Result, ReaderException> resultReaderExceptionPair) throws Exception {
                                    return resultReaderExceptionPair.first.getText();
                                }
                            })
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(mScanResult);

                    observeScanResult();
                }

                @Override
                public void onOpenTorch(boolean torch) {
                    button.setText(torch ? "关灯" : "开灯");
                }

                @Override
                public void needOpenTorch(boolean needOpenTorch) {
                    if (!needOpenTorch) {
                        rxBarCodeScannerFragment.setTorch(false);
                    }
                    button.setEnabled(needOpenTorch);
                }

                @Override
                public void onCameraOpened() {
                    Rect framingRect = scanBoxView.getFramingRect();
                    rxBarCodeScannerFragment.setFocusLocation(framingRect.centerX(), framingRect.centerY());
                }
            });
            rxBarCodeScannerFragment.setContinuousFocus(true);

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rxBarCodeScannerFragment.setTorch(!rxBarCodeScannerFragment.getTorch());
                }
            });
        }
    }

    private void observeScanResult() {
        mScanResultSubscription = mScanResult.subscribe(new Consumer<String>() {

            private AlertDialog mDialog;

            @Override
            public void accept(String s) throws Exception {
                stopObserveScanResult();

                if (mDialog == null) {
                    mDialog = new AlertDialog.Builder(MainActivity.this)
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    observeScanResult();
                                }
                            })
                            .setCancelable(false)
                            .create();
                }

                mDialog.setMessage(s);
                mDialog.show();
            }
        });
    }

    private void stopObserveScanResult() {
        if (mScanResultSubscription != null && !mScanResultSubscription.isDisposed()) {
            mScanResultSubscription.dispose();
            mScanResultSubscription = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopObserveScanResult();
    }
}
