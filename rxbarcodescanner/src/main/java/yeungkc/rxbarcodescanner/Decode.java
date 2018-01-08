package yeungkc.rxbarcodescanner;

import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collection;
import java.util.EnumMap;

public class Decode {

    private static final RxBarCodeScannerLogger LOG = RxBarCodeScannerLogger.create(Decode.class.getSimpleName());

    private static boolean sIsUseHybridBinarizer;

    private final MultiFormatReader mReader;
    private final Collection<BarcodeFormat> mDecodeFormats;
    private final boolean mNeedRotate;

    public Decode(Collection<BarcodeFormat> decodeFormats) {
        mDecodeFormats = decodeFormats;
        mNeedRotate = checkNeedRotate(decodeFormats);
        Log.i("checkNeedRotate", mNeedRotate + "");

        EnumMap<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.CHARACTER_SET, "utf-8");
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, mDecodeFormats);
        mReader = new MultiFormatReader();
        mReader.setHints(hints);
    }

    private boolean checkNeedRotate(Collection<BarcodeFormat> decodeFormats) {
        for (BarcodeFormat decodeFormat : decodeFormats) {
            for (BarcodeFormat oneDFormat : DecodeFormatManager.ONE_D_FORMATS) {
                if (oneDFormat != decodeFormat) continue;

                return true;
            }
        }
        return false;
    }

    public Pair<Result, ReaderException> decode(byte[] data, int width, int height, int format, int rotation, Rect cropRect) {
        long start = System.currentTimeMillis();

        if (cropRect == null && (!mNeedRotate || (rotation % 180 == 0))) {
            rotation = 0;
        }

        SourceData sourceData = new SourceData(data, width, height, format, rotation);
        sourceData.setCropRect(cropRect);
        PlanarYUVLuminanceSource source = sourceData.createSource();

        long rotate = System.currentTimeMillis() - start;

        Binarizer binarizer;
        if (sIsUseHybridBinarizer) {
            binarizer = new HybridBinarizer(source);
        } else {
            binarizer = new GlobalHistogramBinarizer(source);
        }
        sIsUseHybridBinarizer = !sIsUseHybridBinarizer;

        BinaryBitmap bitmap = new BinaryBitmap(binarizer);

        try {
            Result result = mReader.decodeWithState(bitmap);
            return Pair.create(result, null);
        } catch (ReaderException e) {
            return Pair.create(null, e);
        } finally {
            mReader.reset();
            LOG.i(getClass().getSimpleName(), "rotate : " + rotate + ", decode : " + (System.currentTimeMillis() - start));
        }
    }
}
