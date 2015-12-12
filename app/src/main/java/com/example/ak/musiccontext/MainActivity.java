package com.example.ak.musiccontext;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    TextView lightStatus, gyroStatus, magnetStatus, orientationStatus, gameStatus, proxStatus;
    ToggleButton lightToggle, gyroToggle, magnetToggle, orientationToggle, gameToggle, proxToggle;
    MidiInputPort inputPort;

    HashMap<Integer, Integer> controlValues = new HashMap<Integer, Integer>();

    float maxLight = -1, minMagnet = -1;
    final float maxMagnet = 300, maxGame = (float) 0.80, maxGyro = 10;

    private static final String TAG = "SENSORMUSIC";

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        initMidi();

        //Initialize UI components
        lightToggle = (ToggleButton) findViewById(R.id.lightToggle);
        gyroToggle = (ToggleButton) findViewById(R.id.gyroToggle);
        magnetToggle = (ToggleButton) findViewById(R.id.magnetToggle);
        orientationToggle = (ToggleButton) findViewById(R.id.orientToggle);
        gameToggle = (ToggleButton) findViewById(R.id.gameToggle);
        proxToggle = (ToggleButton) findViewById(R.id.proxToggle);
        lightStatus = (TextView) findViewById(R.id.lightStatus);
        gyroStatus = (TextView) findViewById(R.id.gyroStatus);
        magnetStatus = (TextView) findViewById(R.id.magnetStatus);
        orientationStatus = (TextView) findViewById(R.id.orientStatus);
        gameStatus = (TextView) findViewById(R.id.gameStatus);
        proxStatus = (TextView) findViewById(R.id.proxStatus);

        Sensor[] sensors = new Sensor[]{
                sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED),
                sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
                sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        };
        for (Sensor sensor : sensors) {
            sensorManager.registerListener(SensorListener, sensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
        }
        //Initialize reload button
        final Button button = (Button) findViewById(R.id.reload);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                maxLight = -1; minMagnet = -1;
            }
        });
    }

    private final SensorEventListener SensorListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT && lightToggle.isChecked()) {
                final float rawLight = event.values[0];
                if (maxLight == -1) maxLight = rawLight;
                final int midiLight = (int) (Math.min(rawLight, maxLight) / maxLight * 126);
                lightStatus.setText("LIGHT: " + midiLight);
                sendMidiValue(midiLight, 0x07);
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && gyroToggle.isChecked()) {
                final float rawGyro = magnitude(event.values);
                final int midiGyro = (int) (Math.min(rawGyro, maxGyro) / maxGyro * 126);
                gyroStatus.setText("GYRO: " + midiGyro);
                sendMidiValue(midiGyro, 0x08);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED && magnetToggle.isChecked()) {
                final float rawMagnet = event.values[0];
                if (minMagnet == -1) minMagnet = rawMagnet;
                final float magnetRange = Math.max(rawMagnet - minMagnet, 0);
                final int midiMagnet = (int) (Math.min(magnetRange, maxMagnet) / maxMagnet * 126);
                magnetStatus.setText("MAG: " + midiMagnet);
                sendMidiValue(midiMagnet, 0x0A);
            } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION && orientationToggle.isChecked()) {
                final float rawOrientation = Math.abs(event.values[2]);
                final int midiOrientation = (int) (rawOrientation / 90.0 * 126);
                orientationStatus.setText("ORIENT: " + midiOrientation);
                sendMidiValue(midiOrientation, 0x0B);
            } else if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR && gameToggle.isChecked()) {
                final float rawGame = Math.abs(event.values[1]);
                final int midiGame = (int) (rawGame / maxGame * 126);
                gameStatus.setText("GAME: " + midiGame);
                sendMidiValue(midiGame, 0x0C);
            } else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY && proxToggle.isChecked()) {
                final float rawProx = event.values[0];
                proxStatus.setText("PROX: " + rawProx);
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.M)
    public void initMidi() {
        /* Begin Midi Stuff */
        MidiManager m = (MidiManager) this.getSystemService(Context.MIDI_SERVICE);
        final MidiDeviceInfo[] infos = m.getDevices();
        if (infos.length > 0) {
            m.openDevice(infos[0], new MidiManager.OnDeviceOpenedListener() {
                @Override
                public void onDeviceOpened(MidiDevice device) {
                    if (device == null) {
                        toast("could not open device " + infos[0]);
                    } else {
                        inputPort = device.openInputPort(0);
                        toast("midi initialized");
                    }
                }}, new Handler(Looper.getMainLooper()));
        }
        /* End Midi Stuff */
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void sendMidiValue(int value, int controlNumber) {
        final boolean isChanged = controlValues.containsKey(controlNumber)
                                    && (controlValues.get(controlNumber) != value);
        if (inputPort != null && isChanged) {
            byte[] buffer = new byte[32];
            int numBytes = 0;
            buffer[numBytes++] = (byte) 176;
            buffer[numBytes++] = (byte) controlNumber;
            buffer[numBytes++] = (byte) value;
            try {
                inputPort.send(buffer, 0, numBytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            //Log.e(TAG, "could not open device");
        }
        controlValues.put(controlNumber, value);
    }

    public void toast(CharSequence message) {
        Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        toast.show();
    }

    public float magnitude(float[] values) {
        float out = 0;
        for (float value : values) out += value * value;
        return (float) Math.sqrt(out);
    }
}
