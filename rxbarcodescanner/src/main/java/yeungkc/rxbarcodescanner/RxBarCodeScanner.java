package yeungkc.rxbarcodescanner;

import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;

import com.google.zxing.BarcodeFormat;

import java.util.Collection;

public class RxBarCodeScanner {

    public static final String RX_BAR_CODE_SCANNER_FRAGMENT = "rxBarCodeScannerFragment";

    /**
     * @param fragmentManager
     * @param container
     * @param decodeFormats      {@link DecodeFormatManager} or {@link BarcodeFormat}
     * @return
     */
    public static RxBarCodeScannerFragment scanFromCamera(
            @NonNull FragmentManager fragmentManager,
            @IdRes int container,
            Collection<BarcodeFormat> decodeFormats
    ) {
        RxBarCodeScannerFragment fragment = (RxBarCodeScannerFragment) fragmentManager.findFragmentByTag(RX_BAR_CODE_SCANNER_FRAGMENT);

        if (fragment != null) return fragment;

        fragment = RxBarCodeScannerFragment.newInstance(decodeFormats);
        fragmentManager.beginTransaction()
                .add(container, fragment, RX_BAR_CODE_SCANNER_FRAGMENT)
                .commit();

        return fragment;
    }
}
