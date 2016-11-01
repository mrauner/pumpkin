package org.rauner.pumpkin;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TargetApi(21)
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final String TAG = MainActivity.class.getName();
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private BluetoothGattService mBluetoothGattService;
    private BluetoothGattCharacteristic mBluetoothGattCharacteristic;
    private BluetoothGattCharacteristic mRxBluetoothGattCharacteristic;
    private BluetoothGattCharacteristic mTxBluetoothGattCharacteristic;
    private final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private boolean mIsRelayOn = false;
    private boolean mIsLedOn = false;
    private ToggleButton relayButton;
    private ToggleButton ledButton;

    private byte red = (byte) 0x00;
    private byte green = (byte) 0x00;
    private byte blue = (byte) 0x00;
    private byte flick_lo = (byte) 0x00;
    private byte flick_hi = (byte) 0x00;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();

        handleActivityPermissions();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        relayButton = (ToggleButton) findViewById(R.id.relayButton);
        relayButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendRelayMessage(v);
            }
        });
        relayButton.setActivated(false);

        ledButton = (ToggleButton) findViewById(R.id.ledButton);
        ledButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendLedMessage(v);
            }
        });
        ledButton.setActivated(false);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            scanBle();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(filters, mBluetoothAdapter, mHandler, mLEScanner, mLeScanCallback, mScanCallback, settings, false);
        }
    }

    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static void scanLeDevice(List<ScanFilter> filters, final BluetoothAdapter mBluetoothAdapter, Handler mHandler, final BluetoothLeScanner mLEScanner, final BluetoothAdapter.LeScanCallback mLeScanCallback, final ScanCallback mScanCallback, ScanSettings settings, final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);

                    }
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }

    private ScanCallback mScanCallback = new MyScanCallback();

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("onLeScan", device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(filters, mBluetoothAdapter, mHandler, mLEScanner, mLeScanCallback, mScanCallback, settings, false);// will stop after first device detection
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            relayButton.setActivated(true);
                            ledButton.setActivated(true);
                        }
                    });
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    mGatt = null;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            relayButton.setActivated(false);
                            ledButton.setActivated(false);
                        }
                    });
                    scanBle();
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("onServicesDiscovered", services.toString());
            if (null != gatt.getService(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))) {
                mGatt = gatt;
                mBluetoothGattService = gatt.getService(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"));
                Log.i(TAG, "*** FOUND BLE ***");
                if (null != mBluetoothGattService.getCharacteristic(UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))) {
                    // TODO
                    mTxBluetoothGattCharacteristic = mBluetoothGattService.getCharacteristic(UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"));
                    Log.i(TAG, "*** FOUND TX characteristic ***");
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
            gatt.disconnect();
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void handleActivityPermissions() {
        // Here, thisActivity is the current activity
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private class MyScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanBle();
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    }

    private boolean writeData(byte [] data) {

        // Calculate checksum
        byte checksum = 0;
        for (byte aData : data) {
            checksum += aData;
        }
        checksum = (byte) (~checksum);       // Invert

        // Add crc to data
        byte dataCrc[] = new byte[data.length + 1];
        System.arraycopy(data, 0, dataCrc, 0, data.length);
        dataCrc[data.length] = checksum;

        if (null != mTxBluetoothGattCharacteristic) {
            mTxBluetoothGattCharacteristic.setValue(dataCrc);
        } else
            return false;
        boolean retVal = false;
        if (null != mGatt) {
            retVal = mGatt.writeCharacteristic(mTxBluetoothGattCharacteristic);
        }
        Log.i(TAG, "*** Write to Characteristic *** " + data + " " + mTxBluetoothGattCharacteristic.getValue());
        return retVal;
    }

    public void sendRelayMessage(View view) {
        ByteBuffer buffer = ByteBuffer.allocate(3).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0x21).put((byte) 0x42);
        if (mIsRelayOn) {
            buffer.put((byte) 0x30);
        } else {
            buffer.put((byte) 0x31);
        }
        byte [] data = buffer.array();
        if (writeData(data)) {
            mIsRelayOn = !mIsRelayOn;
        }
    }

    public void sendLedMessage(View view) {
        ByteBuffer buffer = ByteBuffer.allocate(7).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0x21).put((byte) 0x43);
        if (mIsLedOn) {
            red = (byte) 0x00;
            green = (byte) 0x00;
            blue = (byte) 0x00;
            flick_lo = (byte) 0x00;
            flick_hi = (byte) 0x00;
        } else {
            red = (byte) 0xcc;
            green = (byte) 0x44;
            blue = (byte) 0x11;
            flick_lo = (byte) 0x0a;
            flick_hi = (byte) 0x40;
        }
        buffer.put(red).put(green).put(blue).put(flick_lo).put(flick_hi);
        byte [] data = buffer.array();
        if (writeData(data)) {
            mIsLedOn = !mIsLedOn;
        };
    }

    private void scanBle() {
        if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filters = new ArrayList<ScanFilter>();
        }
        scanLeDevice(filters, mBluetoothAdapter, mHandler, mLEScanner, mLeScanCallback, mScanCallback, settings, true);
    }

}