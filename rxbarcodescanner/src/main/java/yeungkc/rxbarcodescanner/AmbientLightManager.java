package yeungkc.rxbarcodescanner;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.List;

/**
 * Detects ambient light and switches on the front light when very dark, and off again when sufficiently light.
 *
 * @author Sean Owen
 * @author Nikolaus Huber
 */
public final class AmbientLightManager implements SensorEventListener {

    private static final float TOO_DARK_LUX = 45.0f;
    private static final float BRIGHT_ENOUGH_LUX = 450.0f;

    private final Context mContext;

    private boolean mIsContainLightSensor;

    private Sensor mLightSensor;
    private AmbientLightListener mAmbientLightListener;

    public AmbientLightManager(Context context) {
        this.mContext = context;
        SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : sensorList) {
            if (Sensor.TYPE_LIGHT == sensor.getType()) {
                mIsContainLightSensor = true;
                return;
            }
        }
    }

    public void start(AmbientLightListener ambientLightListener) {
        mAmbientLightListener = ambientLightListener;

        SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            mLightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (mLightSensor != null && mIsContainLightSensor)
                sensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void stop() {
        if (mLightSensor != null) {
            SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) sensorManager.unregisterListener(this);
            mAmbientLightListener = null;
            mLightSensor = null;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float ambientLightLux = sensorEvent.values[0];
        if (mAmbientLightListener != null) {
            if (ambientLightLux <= TOO_DARK_LUX) {
                mAmbientLightListener.needOpenTorch(true);
            } else if (ambientLightLux >= BRIGHT_ENOUGH_LUX) {
                mAmbientLightListener.needOpenTorch(false);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }

    public AmbientLightListener getAmbientLightListener() {
        return mAmbientLightListener;
    }

    public void setAmbientLightListener(AmbientLightListener ambientLightListener) {
        mAmbientLightListener = ambientLightListener;
    }

    public interface AmbientLightListener {
        void needOpenTorch(boolean needOpenTorch);
    }
}
