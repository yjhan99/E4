package com.empatica.sample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Observable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/* Import for the JSON object creation */
import javax.json.*;


public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    /* Static variables */
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;
    private static final String EMPATICA_API_KEY = "6f7968661ed54d468639875a3a9d5920";

    private EmpaDeviceManager deviceManager = null;

    //region INTERFACE LABELS
    /* Accelerometer axis labels */
    private TextView accel_xLabel;
    private TextView accel_yLabel;
    private TextView accel_zLabel;

    /* Physiological sensors labels */
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView temperatureLabel;

    /* Device status info */
    private TextView batteryLabel;
    private TextView statusLabel;
    private TextView deviceNameLabel;
    //endregion

    private LinearLayout dataCnt; //arranges views

    private Long now;
    private String time;

    //region DATA E4 SENSORS VARIABLES
    /* Accelerometer variables */
    private String accel_xData;
    private String accel_yData;
    private String accel_zData;
    private String accel_time;
    private File acccsvFile;
    private boolean accheader = true;
    private JsonObject jsonObjectAcc;

    /* BVP variables */
    private String bvpData;
    private File bvpcsvFile;
    private boolean bvpheader = true;

    /* EDA variables */
    private String edaData;
    private File edacsvFile;
    private boolean edaheader = true;

    /* IBI variables */
    private String ibiData;
    private File ibicsvFile;
    private boolean ibiheader = true;

    /* Temperature variables */
    private String temperatureData;
    private File temperaturecsvFile;
    private boolean temperatureheader = true;

    /* Data Observables */
    private Observable obsAccStream;
    private Observable obsBvpStream;
    private Observable obsEdaStream;
    private Observable obsIbiStream;
    private Observable obsTempStream;
    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        //region Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);

        dataCnt = (LinearLayout) findViewById(R.id.dataArea);
        now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        time = dateFormat.format(date);

        accel_xLabel = (TextView) findViewById(R.id.accel_x);
        accel_yLabel = (TextView) findViewById(R.id.accel_y);
        accel_zLabel = (TextView) findViewById(R.id.accel_z);
        //acccsvFile = new File(getFilesDir(), time+" acc.txt");
        acccsvFile = new File(getFilesDir(), time+" acc.json");

        bvpLabel = (TextView) findViewById(R.id.bvp);
        bvpcsvFile = new File(getFilesDir(), time+" bvp.txt");
        //bvpcsvFile = new File(getFilesDir(), time+" bvp.json");

        edaLabel = (TextView) findViewById(R.id.eda);
        edacsvFile = new File(getFilesDir(), time+" eda.txt");
        //edacsvFile = new File(getFilesDir(), time+" eda.json");

        ibiLabel = (TextView) findViewById(R.id.ibi);
        ibicsvFile = new File(getFilesDir(), time+" ibi.txt");
        //ibicsvFile = new File(getFilesDir(), time+" ibi.json");

        temperatureLabel = (TextView) findViewById(R.id.temperature);
        temperaturecsvFile = new File(getFilesDir(), time+" temp.txt");
        //temperaturecsvFile = new File(getFilesDir(), time+" temp.json");

        batteryLabel = (TextView) findViewById(R.id.battery);

        deviceNameLabel = (TextView) findViewById(R.id.deviceName);
        //endregion

        final Button disconnectButton = findViewById(R.id.disconnectButton);

        disconnectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (deviceManager != null) {

                    deviceManager.disconnect();
                }
            }
        });

        initEmpaticaDeviceManager();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_ACCESS_COARSE_LOCATION:
                // If request is cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted
                    initEmpaticaDeviceManager();
                } else {
                    // Permission denied
                    final boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);
                    new AlertDialog.Builder(this)
                            .setTitle("Permission required")
                            .setMessage("Without this permission bluetooth low energy devices cannot be found; allow it in order to connect to the device.")
                            .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // try again
                                    if (needRationale) {
                                        // the "never ask again" flash is not set, try again with permission request
                                        initEmpaticaDeviceManager();
                                    } else {
                                        // the "never ask again" flag is set so the permission requests is disabled, try open app settings to enable the permission
                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                                        intent.setData(uri);
                                        startActivity(intent);
                                    }
                                }
                            })
                            .setNegativeButton("Exit application", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // without permission exit is the only way
                                    finish();
                                }
                            })
                            .show();
                }
                break;
        }
    }

    private void initEmpaticaDeviceManager() {
        // Android 6 (API level 23) now require ACCESS_COARSE_LOCATION permission to use BLE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
        } else {

            if (TextUtils.isEmpty(EMPATICA_API_KEY)) {
                new AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage("Please insert your API KEY")
                        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // without permission exit is the only way
                                finish();
                            }
                        })
                        .show();
                return;
            }

            // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
            deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

            // Initialize the Device Manager using your API key. You need to have Internet access at this point.
            deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    @Override
    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        /*
         Check if the discovered device can be used with your API key. If allowed is always false,
         the device is not linked with your API key. Please check your developer area at
         https://www.empatica.com/connect/developer.php
        */

        Log.i(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed);

        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed + " - ConnectionNotAllowedException", e);
            }
        }
    }

    @Override
    public void didFailedScanning(int errorCode) {
        
        /*
         A system error occurred while scanning.
         @see https://developer.android.com/reference/android/bluetooth/le/ScanCallback
        */
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                Log.e(TAG,"Scan failed: a BLE scan with the same settings is already started by the app");
                break;
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                Log.e(TAG,"Scan failed: app cannot be registered");
                break;
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                Log.e(TAG,"Scan failed: power optimized scan feature is not supported");
                break;
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                Log.e(TAG,"Scan failed: internal error");
                break;
            default:
                Log.e(TAG,"Scan failed with unknown error (errorCode=" + errorCode + ")");
                break;
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void bluetoothStateChanged() {
        // E4link detected a bluetooth adapter change
        // Check bluetooth adapter and update your UI accordingly.
        boolean isBluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled();
        Log.i(TAG, "Bluetooth State Changed: " + isBluetoothOn);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {

        didUpdateOnWristStatus(status);
    }

     @Override
    public void didUpdateStatus(@NonNull EmpaStatus status) {
        // Update the UI
        updateLabel(statusLabel, status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            deviceManager.startScanning();
            // The device manager has established a connection

            hide();

        } else if (status == EmpaStatus.CONNECTED) {

            show();
            // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {

            updateLabel(deviceNameLabel, "");

            hide();
        }
    }

    @Override // update and record accelerometer data
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        /* Updating accelerometer labels converting values to String */
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);

        /* Update accelerometer variables */
        accel_xData = Integer.toString(x);
        accel_yData = Integer.toString(y);
        accel_zData = Integer.toString(z);
        accel_time = Double.toString(timestamp);

        BufferedWriter bw = null;

        /* Create JSON object that must be added to the file */
        jsonObjectAcc = Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("accX", accel_xData)
                .add("accY", accel_yData)
                .add("accZ", accel_zData)
                .build().asJsonObject();

        try {
            // if there's no file to write into, try creating one
            try {
                if (!acccsvFile.exists()) {
                    acccsvFile.createNewFile();
                    accheader = false;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // buffer initialization
            bw = new BufferedWriter(new FileWriter(acccsvFile, true));

            /*
            // create a header, if there's none
            if (accheader == false) {
                bw.write("Timestamp, accX, accY, accZ");
                bw.newLine(); // new line
                accheader = true;
            }

            // create and initialize the variable containing the data
            String data = "";
            data = timestamp + "," + accel_xData + "," + accel_yData + "," + accel_zData;

            bw.write(data);
            bw.newLine();
            */
            bw.write(jsonObjectAcc.toString());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                // empty the buffer
                if (bw != null) {
                    bw.flush();
                    bw.close();
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return;
    }

    @Override // update and record BVP data
    public void didReceiveBVP(float bvp, double timestamp) {
        /* Update label and BVP data variable */
        updateLabel(bvpLabel, "" + bvp);
        bvpData = Float.toString(bvp);

        BufferedWriter bw = null;
        try {
            // if there's no file to write into, try creating one
            try {
                if (!bvpcsvFile.exists()) {
                    bvpcsvFile.createNewFile();
                    bvpheader = false;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // buffer initialization
            bw = new BufferedWriter(new FileWriter(bvpcsvFile, true));

            // create a header, if there's none
            if (bvpheader == false) {
                bw.write("Timestamp, bvp");
                bw.newLine(); // new line
                bvpheader = true;
            }

            // create and initialize the variable containing the data
            String data = "";
            data = timestamp + "," + bvpData;

            bw.write(data);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                // empty the buffer
                if (bw != null) {
                    bw.flush();
                    bw.close();
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return;
    }

    @Override // update battery label
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
    }

    @Override // update and record GSR (aka EDA) data
    public void didReceiveGSR(float gsr, double timestamp) {
        /* Update label and BVP data variable */
        updateLabel(edaLabel, "" + gsr);
        edaData = Float.toString(gsr);

        BufferedWriter bw = null;
        try {
            // if there's no file to write into, try creating one
            try {
                if (!edacsvFile.exists()) {
                    edacsvFile.createNewFile();
                    edaheader = false;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            bw = new BufferedWriter(new FileWriter(edacsvFile, true));

            // create a header, if there's none
            if (edaheader == false) {
                bw.write("Timestamp, eda");
                bw.newLine(); // 개행
                edaheader = true;
            }

            // create and initialize the variable containing the data
            String data = "";
            data = timestamp + "," + edaData;

            bw.write(data); // write the data
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                // empty the buffer
                if (bw != null) {
                    bw.flush();
                    bw.close();
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return;
    }

    @Override // update and record IBI data
    public void didReceiveIBI(float ibi, double timestamp) {
        updateLabel(ibiLabel, "" + ibi);
        ibiData = Float.toString(ibi);

        BufferedWriter bw = null;

        try {
            try {
                if (!ibicsvFile.exists()) {
                    ibicsvFile.createNewFile();
                    ibiheader = false;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            bw = new BufferedWriter(new FileWriter(ibicsvFile, true));

            if (ibiheader == false) {
                bw.write("Timestamp, ibi");
                bw.newLine(); // 개행
                ibiheader = true;
            }

            String data = "";
            data = timestamp + "," + ibiData;

            bw.write(data);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.flush();
                    bw.close();
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return;
    }

    @Override // update and record temperature data
    public void didReceiveTemperature(float temp, double timestamp) {
        updateLabel(temperatureLabel, "" + temp);
        temperatureData = Float.toString(temp);

        BufferedWriter bw = null;

        try {
            try {
                if (!temperaturecsvFile.exists()) {
                    temperaturecsvFile.createNewFile();
                    temperatureheader = false;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            bw = new BufferedWriter(new FileWriter(temperaturecsvFile, true));

            if (temperatureheader == false) {
                bw.write("Timestamp, temp");
                bw.newLine(); // 개행
                temperatureheader = true;
            }

            String data = "";
            data = timestamp + "," + temperatureData;

            bw.write(data);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.flush();
                    bw.close();
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return;
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    @Override
    public void didReceiveTag(double timestamp) {

    }

    @Override // show established connection
    public void didEstablishConnection() {

        show();
    }

    @Override // on-wrist detection
    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                if (status == EmpaSensorStatus.ON_WRIST) {

                    ((TextView) findViewById(R.id.wrist_status_label)).setText("ON WRIST");
                }
                else {

                    ((TextView) findViewById(R.id.wrist_status_label)).setText("NOT ON WRIST");
                }
            }
        });
    }


    //region LOCAL METHODS
    void show() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                dataCnt.setVisibility(View.VISIBLE);
            }
        });
    }

    void hide() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                dataCnt.setVisibility(View.INVISIBLE);
            }
        });
    }
    //endregion
}
