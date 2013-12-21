package com.pirhoalpha.ubiplug;

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
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback{
	
	private boolean mScanning;	//Boolean to store state of scanning
	private TextView noOfDevices;	//TextView to show no of devices found
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGatt mConnectedGatt;
	private SparseArray<BluetoothDevice> mDevices;
	private Handler mHandler;
	private ProgressBar mProgress;	//ProgressBar to show scanning progress
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
		noOfDevices = (TextView)findViewById(R.id.noOfDevices);
		noOfDevices.setText("");
		mProgress = (ProgressBar)findViewById(R.id.progressBar);
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
    
    /**
     * Function which starts scanning
     */
    private void startScan() 
    {
        mBluetoothAdapter.startLeScan(this);
        
        mHandler.postDelayed(mStopRunnable, 2500);
    }

    /**
     * Function which stops scanning
     */
    private void stopScan() 
    {
        mBluetoothAdapter.stopLeScan(this);
        setProgressBarIndeterminateVisibility(false);
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
            mDevices.put(device.hashCode(), device);
            invalidateOptionsMenu();
        }
    }
}
