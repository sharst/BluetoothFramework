/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.uos.nbp.senhance.bluetooth;

import java.util.HashSet;
import java.util.Set;

import de.uos.nbp.senhance.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This Activity appears as a dialog. It lists paired devices and devices
 * detected in the area after discovery, as long as they match the
 * regular expression provided by the calling activity.
 * When a device is chosen by the user, the MAC address of the device
 * is sent back to the parent Activity in the result Intent.
 */
public class DeviceListActivity extends Activity {
	private static final String TAG = "Senhance";
	private static final boolean D = true; /* debugging */

	/* Intent Extras */
	public static final String INTENT_DEVICENAMEFILTER = "de.uos.nbp.senhance.deviceNameFilter";
	public static final String INTENT_DEVICETYPE = "de.uos.nbp.senhance.deviceType";
	public static final String INTENT_DEVICEID = "de.uos.nbp.senhance.deviceID";
	public static final String INTENT_DEVICEADDRESS = "de.uos.nbp.senhance.deviceAddress";

	private static final int REQUEST_ENABLE_BT = 2;

	/* Member fields */
	private BluetoothAdapter mBtAdapter;
	private ArrayAdapter<String> mPairedDevicesArrayAdapter;
	private ArrayAdapter<String> mNewDevicesArrayAdapter;
	private String mDeviceType;
	private int mID;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (D)
			Log.v(TAG, "DeviceListActivity.onCreate()");

		// Setup the window
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.device_list);

		/*
		 * Set result CANCELED in case activity ends without going through the
		 * explicit finishWith...() functions.
		 */
		setResult(Activity.RESULT_CANCELED);

		// Get the local Bluetooth adapter
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBtAdapter == null) {
			Log.e(TAG, "DeviceListActivity|No bluetooth adapter found on this device.");
			finishWithCancel();
			return;
		}

		/*
		 * If bluetooth is not enabled at the moment, then request it to be
		 * enabled.
		 */
		if (!mBtAdapter.isEnabled()) {
			Log.i(TAG, "DeviceListActivity|requesting bluetooth enable...");
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else {
			Log.i(TAG, "DeviceListActivity|Bluetooth is enabled");
		}

		// Initialize array adapters. One for already paired devices and
		// one for newly discovered devices
		mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.device_name);
		mNewDevicesArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.device_name);

		// Find and set up the ListView for paired devices
		ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
		pairedListView.setAdapter(mPairedDevicesArrayAdapter);
		pairedListView.setOnItemClickListener(mDeviceClickListener);

		// Find and set up the ListView for newly discovered devices
		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
		newDevicesListView.setOnItemClickListener(mDeviceClickListener);

		// Get a set of currently paired devices
		Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
		Set<BluetoothDevice> pairedECGDevices = new HashSet<BluetoothDevice>();

		mID = getIntent().getIntExtra(INTENT_DEVICEID, -1);
		mDeviceType = getIntent().getStringExtra(INTENT_DEVICETYPE);
		String deviceNameFilter = getIntent().getStringExtra(
				INTENT_DEVICENAMEFILTER);
		
		doSetTitle(getString(R.string.select_device));
		if (D) Log.v(TAG, "DeviceListActivity|"+mDeviceType+"("+mID+") ["+deviceNameFilter+"]");

		/*
		 * Remove all non-matching devices from the set. We are interested
		 * only in those devices that match the given regular expression.
		 */
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				/* is name the best way to identify this? */
				if ((deviceNameFilter != null) && (device.getName().matches(deviceNameFilter))) {
						pairedECGDevices.add(device);
				// If there is no filter, add all devices
				} else if (deviceNameFilter == null) {
					pairedECGDevices.add(device);
				}
			}
		}
		if (pairedECGDevices.size() > 0) {
			/* if there remain paired devices, action them */
			findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
			if (D)
				Log.i(TAG, "DeviceListActivity|found "
						+ pairedECGDevices.size() + " paired devices.");
			if (pairedECGDevices.size() == 1) {

				/*
				 * We want to automatically respond if there is only a single
				 * device matching the filter.
				 */
				BluetoothDevice dev = pairedECGDevices.iterator().next();
				String address = dev.getAddress();
				finishWithSuccess(address);
			} else {
				/*
				 * if there are more than one appropriate device, add each one
				 * to the ArrayAdapter
				 */
				for (BluetoothDevice device : pairedECGDevices) {
					mPairedDevicesArrayAdapter.add(device.getName() + "\n"
							+ device.getAddress());
				}
			}
		} else {
			String noDevices = getResources().getText(R.string.none_paired)
					.toString();
			mPairedDevicesArrayAdapter.add(noDevices);
		}

		// Initialize the button to perform device discovery
		Button scanButton = (Button) findViewById(R.id.button_scan);
		scanButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				doDiscovery();
				v.setVisibility(View.GONE);
			}
		});

		Button cancelButton = (Button) findViewById(R.id.button_cancel);
		cancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finishWithCancel();
			}
		});

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		if (D)
			Log.d(TAG, "DeviceListActivity.onResume()");

		super.onResume();

		// Register for broadcasts when a device is discovered
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);

		// Register for broadcasts when discovery has finished
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		if (D)
			Log.d(TAG, "DeviceListActivity.onPause()");
		// Unregister broadcast listeners
		this.unregisterReceiver(mReceiver);

		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (D)
			Log.d(TAG, "DeviceListActivity.onDestroy()");

		// Make sure we're not doing discovery anymore
		if (mBtAdapter != null) {
			mBtAdapter.cancelDiscovery();
		}
		super.onDestroy();
	}

	/**
	 * Finish this activity successfully and return to caller, specifying in the
	 * Intent the address of the found device.
	 * 
	 * The device ID that was passed on creation is also returned to allow the
	 * calling activity to identify for which device this search was conducted.
	 * 
	 * @param address
	 */
	private void finishWithSuccess(String address) {
		/* Create the result Intent and include the MAC address and deviceID. */
		Intent intent = new Intent();
		intent.putExtra(INTENT_DEVICEID, mID); 
		intent.putExtra(INTENT_DEVICEADDRESS, address);

		/* set result and finish this Activity */
		setResult(Activity.RESULT_OK, intent);
		finish();
	}

	/**
	 * Finish this activity and return to caller indicating non-success - that
	 * is, search canceled.
	 * 
	 * The device ID that was passed on creation is also returned to allow the
	 * calling activity to identify for which device this search was conducted.
	 */
	private void finishWithCancel() {
		Intent intent = new Intent();
		intent.putExtra(INTENT_DEVICEID, mID);
		if (D) Log.v(TAG, "DeviceListActivity|finishWithCancel("+mID+")");

		setResult(Activity.RESULT_CANCELED, intent);
		finish();
	}

	private void doSetTitle(String txt) {
		StringBuilder sb = new StringBuilder();

		if (mDeviceType != null)
			sb.append(mDeviceType).append(": ");
		sb.append(txt);
		setTitle(sb.toString());
	}

	/**
	 * Start device discover with the BluetoothAdapter
	 */
	private void doDiscovery() {
		if (D)
			Log.d(TAG, "DeviceListActivity|doDiscovery()");

		// Indicate scanning in the title
		setProgressBarIndeterminateVisibility(true);
		doSetTitle(getString(R.string.scanning));

		// Turn on sub-title for new devices
		findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

		if (mBtAdapter != null) {
			// If we're already discovering, stop it
			if (mBtAdapter.isDiscovering()) {
				mBtAdapter.cancelDiscovery();
			}

			// Request discover from BluetoothAdapter
			mBtAdapter.startDiscovery();
		}
	}

	// The on-click listener for all devices in the ListViews
	private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
			/*
			 * Cancel discovery because it's costly and we're about to connect.
			 */
			if (D)
				Log.v(TAG, "DeviceListActivity|onItemClick()");
			if (mBtAdapter.isDiscovering())
				mBtAdapter.cancelDiscovery();

			/*
			 * Get the device MAC address, which is the last 17 chars in the
			 * View
			 */
			String info = ((TextView) v).getText().toString();
			String address = info.substring(info.length() - 17);

			finishWithSuccess(address);
		}
	};

	// The BroadcastReceiver that listens for discovered devices and
	// changes the title when discovery is finished
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// If it's already paired, skip it, because it's been listed
				// already
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					mNewDevicesArrayAdapter.add(device.getName() + "\n"
							+ device.getAddress());
				}
				// When discovery is finished, change the Activity title
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
					.equals(action)) {
				setProgressBarIndeterminateVisibility(false);
				doSetTitle(getString(R.string.select_device));

				if (mNewDevicesArrayAdapter.getCount() == 0) {
					String noDevices = getResources().getText(
							R.string.none_found).toString();
					mNewDevicesArrayAdapter.add(noDevices);
				}
			}
		}
	};

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == RESULT_CANCELED) {
				finishWithCancel();
			}
			break;
		}
	}
}
