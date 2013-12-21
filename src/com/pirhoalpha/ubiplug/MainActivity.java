package com.pirhoalpha.ubiplug;

import java.util.ArrayList;
import java.util.UUID;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Message;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback{
	
	private boolean mScanning;	//Boolean to store state of scanning
	private TextView messageView;	//TextView to show no of devices found
    private ListView deviceListView;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGatt mConnectedGatt;
	private SparseArray<BluetoothDevice> mDevices;
	private Handler mHandler;
	private ProgressBar mProgress;	//ProgressBar to show scanning progress
    public static ArrayList<BluetoothDevice> myBTDevices = new ArrayList<BluetoothDevice>();
    private ArrayAdapter<String> mArrayAdapter;
	private static final String TAG = "BluetoothGattActivity";
	private final static int REQUEST_ENABLE_BT = 1;
	private static final String DEVICE_NAME = "Device Name";	
	private static final UUID UBIPLUG_SERVICE
		= UUID.fromString("a6322521-eb79-4b9f-9152-19daa4870418");	//UUID of service name
	private static final UUID UBIPLUG_DATA_CHAR 
		= UUID.fromString("f90ea017-f673-45b8-b00b-16a088a2ed61");	//UUID of data characteristics
	
	//TODO SET THE MISSING UUID
	//private static final UUID UBIPLUG_CONFIG_CHAR 
		//= UUID.fromString("f000aa22-0451-4000-b000-000000000000");	//This is missing parameter which is required
	

	/**
	 * This is the first method called when a user clicks on the
	 * app icon to start the app
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_main);
		messageView = (TextView)findViewById(R.id.message_view);
		messageView.setText("");
		mProgress = (ProgressBar)findViewById(R.id.progressBar);
		deviceListView = (ListView)findViewById(R.id.listView);
		BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = manager.getAdapter();
	}
	
	/**
	 *This method is called when app is visible to the user but the user
	 *can not interact with the app.
	 *It is like a glass door where a person can see the person on other side
	 *but can not interact(talk, shake hands, etc.) 
	 */
	@Override
	protected void onStart() {
		super.onStart();
	}
	
	/**
	 * This method is called when the user can interact with the app.
	 * It is the opener of the glass door discussed above.
	 * 
	 * Here this method turns on bluetooth if it is off.
	 * This method also closes the application if the device does not support BLE
	 * However in production the play store will keep this app from
	 * installing on unsupported devices
	 */
	@Override
	protected void onResume() {
		super.onResume();
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) 
		{
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		    finish();
		    return;
		}
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) 
		{
		    Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
		    finish();
		    return;
		}
        mArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
	}
	
	/**
	 * This method is called when a user opens another app without closing this app
	 */
    @Override
    protected void onPause() 
    {
        super.onPause();
        //Make sure dialog is hidden
        mProgress.setVisibility(0);
        //Cancel any scans in progress
        mHandler.removeCallbacks(mStopRunnable);
        mHandler.removeCallbacks(mStartRunnable);
        mBluetoothAdapter.stopLeScan(this);
    }
    
    /**
     * This method is called when a user terminates the app
     * Here we are disconnecting from any active tag connection
     */
    @Override
    protected void onStop() 
    {
        super.onStop();
        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt = null;
        }
    }
    
	/**
	 * Callback which starts scanning
	 */
    private Runnable mStopRunnable = new Runnable() 
    {
        @Override
        public void run() {
            stopScan();
        }
    };
    
    /**
     * Callback which stops scanning
     */
    private Runnable mStartRunnable = new Runnable() 
    {
        @Override
        public void run() {
            startScan();
        }
    };
    
    /**
     * This method creates menu in the action bar
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
        // Add the "scan" option to the menu
        getMenuInflater().inflate(R.menu.main, menu);
        //Add any device elements we've discovered to the overflow menu
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName());
        }
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                mDevices.clear();
                startScan();
                return true;
            default:
                //Obtain the discovered device to connect with
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to "+device.getName());
                /*
                 * Make a connection with the device using the special LE-specific
                 * connectGatt() method, passing in a callback for GATT events
                 */
                mConnectedGatt = device.connectGatt(this, true, mGattCallback);
                //Display progress UI
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to "+device.getName()+"..."));
                return super.onOptionsItemSelected(item);
        }
    }
    /**
     * Function which starts scanning
     */
    private void startScan() 
    {
        mBluetoothAdapter.startLeScan(this);
        mProgress.setVisibility(1);
        mHandler.postDelayed(mStopRunnable, 2500);
    }

    /**
     * Function which stops scanning
     */
    private void stopScan() 
    {
        mBluetoothAdapter.stopLeScan(this);
        mProgress.setVisibility(0);
    }


    /**
     * This method deals with part of processing while scan is being conducted.
     */
    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) 
    {
        Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);
        if (DEVICE_NAME.equals(device.getName())) 
        {
        	mArrayAdapter.add(device.getName() + "  " + device.getAddress());
        	mArrayAdapter.notifyDataSetChanged();
            mDevices.put(device.hashCode(), device);
            
        }
    }
    
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /* State Machine Tracking */
        private int mState = 0;

        private void reset() { mState = 0; }

        private void advance() { mState++; }

        /*
         * Send an enable command to each sensor by writing a configuration
         * characteristic.  This is specific to the SensorTag to keep power
         * low by disabling sensors you aren't using.
         */
        private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

                         

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Log.d(TAG, "Connection State Change: "+status+" -> "+connectionState(newState));
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    /*
                     * Once successfully connected, we must next discover all the services on the
                     * device before we can read and write their characteristics.
                     */
                    gatt.discoverServices();
                    mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
                } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                    /*
                     * If at any point we disconnect, send a message to clear the weather values
                     * out of the UI
                     */
                    mHandler.sendEmptyMessage(MSG_CLEAR);
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    /*
                     * If there is a failure at any stage, simply disconnect
                     */
                    gatt.disconnect();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                Log.d(TAG, "Services Discovered: "+status);
                /*
                 * With services discovered, we are going to reset our state machine and start
                 * working through the sensors we need to enable
                 */
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                //For each read, pass the data up to the UI thread to update the display
                if (UBIPLUG_DATA_CHAR.equals(characteristic.getUuid())) {
                    mainHandler.sendMessage(Message.obtain(null, MSG_CURRENT, characteristic));
                }

                //After reading the initial value, next we enable notifications
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                /*
                 * After notifications are enabled, all updates from the device on characteristic
                 * value changes will be posted here.  Similar to read, we hand these up to the
                 * UI thread to update the display.
                 */
                if (UBIPLUG_DATA_CHAR.equals(characteristic.getUuid())) {
                    mainHandler.sendMessage(Message.obtain(null, MSG_CURRENT, characteristic));
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                //Once notifications are enabled, we move to the next sensor and start over with enable
                advance();
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                Log.d(TAG, "Remote RSSI: "+rssi);
            }

            private String connectionState(int status) {
                switch (status) {
                    case BluetoothProfile.STATE_CONNECTED:
                        return "Connected";
                    case BluetoothProfile.STATE_DISCONNECTED:
                        return "Disconnected";
                    case BluetoothProfile.STATE_CONNECTING:
                        return "Connecting";
                    case BluetoothProfile.STATE_DISCONNECTING:
                        return "Disconnecting";
                    default:
                        return String.valueOf(status);
                }
            }
        };
    };
    
    private static final int MSG_CURRENT = 103;
    private static final int MSG_PROGRESS = 201;
    private static final int MSG_DISMISS = 202;
    private static final int MSG_CLEAR = 301;
    private Handler mainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattCharacteristic characteristic;
            switch (msg.what) {
                case MSG_CURRENT:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining cal value");
                        return;
                    }
                    messageView.setText(characteristic.getStringValue(1));
                    break;
                case MSG_PROGRESS:
                    messageView.setText((String) msg.obj);
                    break;
                case MSG_DISMISS:
                    break;
                case MSG_CLEAR:
                	messageView.setText("");
                    break;
            }
        }
    };
    
    

}
