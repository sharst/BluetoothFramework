package de.uos.nbp.senhance.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class BluetoothService {

	public static final String MESSAGE = "MESSAGE";
	// Message types sent to Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int CONNECT_FAILED = 5;
	public static final int CONNECT_ATTEMPT_FAILED = 6;
	public static final int CONNECTION_LOST = 7;
	public static final int CONNECTION_CLOSED = 8;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0; // we're doing nothing
	public static final int STATE_LISTEN = 1; // now listening for incoming connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
	public static final int STATE_CONNECTED = 3; // now connected to a remote device

	// UUID for connection
	private static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static final String NAME = "BluetoothService";

	// Member fields
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private AcceptThread mSecureAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;
	/** Default RFCOMM channel */
	private int mPort = 1;

	public Method m;
	private BluetoothDevice mDevice;

	/**
	 * This is an important variable - Device and Monitoring threads will exit
	 * when this is true.
	 */
	private volatile boolean mConnectionDeliberatelyClosed = false;
	private UUID mUUID;
	private int mDesiredDeviceThreadPriority = -1;
	
	/** Number of times to try a dropped connection or failed connection attempt */
	private int mMaxContiguousConnectionFailures = 3;
	
	/** msecs between repeated connection attempts */
	private long mConnectionAttemptInterval = 1000;
	
	int contiguousConnectionFailures = 0;

	public BluetoothService(Handler handler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = handler;
	}

	public synchronized void setState(int state) {
		mState = state;
		// Send back to UI
		mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
	}

	public synchronized int getState() {
		return mState;
	}

	// This is making the Bluetooth service listen as a server
	public synchronized void startListening() {
		// Cancel any other connections
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		setState(STATE_LISTEN);

		// Start listening thread
		if (mSecureAcceptThread == null) {
			mSecureAcceptThread = new AcceptThread();
			mSecureAcceptThread.start();
		}
	}

	public synchronized void connect(String deviceAddress) {
		this.connect(deviceAddress, mPort);
	}

	public synchronized void connect(String deviceAddress, int port) {
		mDevice = mAdapter.getRemoteDevice(deviceAddress);
		mPort = port;
		connect(mDevice);
	}

	public synchronized void connect(String deviceAddress, UUID uuid){
		mDevice = mAdapter.getRemoteDevice(deviceAddress);
		mUUID = uuid;
		connect(mDevice);
	}
	
	// This is making the Bluetooth Service connect to another server
	public synchronized void connect(BluetoothDevice device) {
		if (mState == STATE_CONNECTING) {
			// Cancel any other connections
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(STATE_CONNECTING);

	}

	// device as parameter is only needed if we want to send its name back to
	// UI.
	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice device) {
		// Cancel any concurrent threads
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		// And the thread that accepted the connection.
		if (mSecureAcceptThread != null) {
			mSecureAcceptThread.cancel();
			mSecureAcceptThread = null;
		}

		mConnectedThread = new ConnectedThread(socket);
		if (mDesiredDeviceThreadPriority!=-1){
			mConnectedThread.setPriority(mDesiredDeviceThreadPriority);
		}
		mConnectedThread.start();

		setState(STATE_CONNECTED);
	}

	// In case all threads need to be stopped
	public synchronized void stop() {
		mConnectionDeliberatelyClosed = true;
		System.out.println("BluetoothService STOP");
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		if (mSecureAcceptThread != null) {
			mSecureAcceptThread.cancel();
			mSecureAcceptThread = null;
		}
		setState(STATE_NONE);
		mHandler.obtainMessage(CONNECTION_CLOSED).sendToTarget();
	}

	public void write(byte[] out) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		// TODO: rmuil: this sync is probably useless. Multiple threads can
		//  get a reference to the mConnectedThread and interleave calls on it. 
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectFailed(String description) {
		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(CONNECT_FAILED);
		Bundle bundle = new Bundle();
		if (description == null) {
			description = "Unable to connect device";
		}
		bundle.putString(MESSAGE, description);
		msg.setData(bundle);
		mHandler.sendMessage(msg);
		// BluetoothService.this.start();

	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 * @param message 
	 */
	private void connectionLost(String message) {
		// Send a failure message back to the Activity
		// Handle connectionLost
		Message msg = mHandler.obtainMessage(CONNECTION_LOST);
		Bundle bundle = new Bundle();
		bundle.putString(MESSAGE, "Device connection was lost. "+message);
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		System.out.println("ConnectedThread: CONNECTION LOST "+message);

		// Start the service over to restart listening mode
		// BluetoothService.this.start();

	}

	// This listens for incoming connections.
	private class AcceptThread extends Thread {
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread() {
			BluetoothServerSocket tmp = null;

			try {
				tmp = mAdapter
						.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
			} catch (IOException e) {

			}

			mmServerSocket = tmp;
		}

		public void run() {
			BluetoothSocket socket = null;
			while (mState != STATE_CONNECTED) {
				try {
					socket = mmServerSocket.accept();
				} catch (IOException e) {
				}

				if (socket != null) {
					synchronized (BluetoothService.this) {
						switch (mState) {
						case STATE_LISTEN:
						case STATE_CONNECTING:
							connected(socket, socket.getRemoteDevice());
							break;
						case STATE_NONE:
						case STATE_CONNECTED:
							try {
								closeSocket(socket);
							} catch (IOException e) {
							}
							break;
						}
					}
				}
			}
		}

		public void cancel() {
			try {
				mmServerSocket.close();
			} catch (IOException e) {

			}
		}
	}

	private class ConnectThread extends Thread {
		private BluetoothDevice mmDevice;
		private BluetoothSocket mmSocket;

		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;
		}

		public void run() {
			mAdapter.cancelDiscovery();


			String message = "";
			boolean connected = false;
			// As long as the connection hasn't been deliberately closed, we should try reconnects
			// (that is, if the number of max connects isn't reached)
			while ((!mConnectionDeliberatelyClosed) 
					&& ((mMaxContiguousConnectionFailures==-1) || (contiguousConnectionFailures < mMaxContiguousConnectionFailures)) ) {
				//Here every time a socket is created, because for some reason, when first the target is
				//unavailable and then available socket.connect hangs (at least for 2.3.3) 
				try {
					createSocket();
				} catch (Exception e) {
					connectFailed(e.getLocalizedMessage());
					return;
				}

				try {
					System.out.println("ConnectThread: Trying to connect...");
					mmSocket.connect();
					System.out.println("ConnectThread: connected");
					connected = true;
					break;
				} catch (Exception e) {
					message = "BluetoothConnection|socket.connect() failed: "
							+ e.getLocalizedMessage();
					contiguousConnectionFailures++;
					mHandler.obtainMessage(CONNECT_ATTEMPT_FAILED).sendToTarget();
					try {
						System.out.println("ConnectThread: Sleeping "+mConnectionAttemptInterval+" ms");
						Thread.sleep(mConnectionAttemptInterval);
					} catch (InterruptedException ignored) {}
				}
			}

			//TODO: has to be done before calling connected. Not very good. Refactor.
			synchronized (BluetoothService.this) {
				mConnectThread = null;
			}
			
			if (!mConnectionDeliberatelyClosed){
				if (connected){
					connected(mmSocket, mmDevice);
				}else {
					try {
						closeSocket(mmSocket);
					} catch (IOException e2) {
						message += "\n socket.close() failed too: "+ e2.getLocalizedMessage();
					}
					connectFailed(message);
				}
			}
			contiguousConnectionFailures = 0;
		}

		private void createSocket() throws Exception {
			if (mUUID!=null){
				mmSocket = mDevice.createRfcommSocketToServiceRecord(mUUID);
			}else{
				Method m = mmDevice.getClass().getMethod("createRfcommSocket",
					new Class[] { int.class });
				mmSocket = (BluetoothSocket) m.invoke(mmDevice, mPort);
			// tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
			}
		}

		public void cancel() {
			try {
				closeSocket(mmSocket);
			} catch (IOException e) {

			}
		}
	}

	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {

			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			int reading;
			int packetReceived = 0;

			while (true) {
				try {
					reading = mmInStream.read();
					packetReceived += 1;
					mHandler.obtainMessage(MESSAGE_READ, reading,packetReceived).sendToTarget();

				} catch (IOException e) {
					mConnectedThread = null;
					if (!mConnectionDeliberatelyClosed){
						connectionLost(e.getLocalizedMessage());
						// In case the autoconnect option has been specified, 
						// directly try to reconnect. 
						if (contiguousConnectionFailures==-1) {
							connect(mDevice);
						}
					}
					break;
				}
			}
		}

		public void write(byte[] buffer) {
			try {
				Bundle bundle = new Bundle();
				
				mmOutStream.write(buffer);

				bundle.putByteArray("value", buffer);
				// Send received message to UI
				Message msg = mHandler.obtainMessage(MESSAGE_WRITE, -1, -1,
						buffer);
				msg.setData(bundle);
				msg.sendToTarget();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void cancel() {
			try {
				closeSocket(mmSocket);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void flush() {
			try {
				mmOutStream.flush();
				/* TODO: this sometimes generates null-pointer exception */
			} catch (Exception ignored) {
				//nop
			}
		}

	}

	public String getDeviceName() {
		return mDevice.getName();
	}

	public void flush() {
		if (mConnectedThread != null) {
			mConnectedThread.flush();
		}
	}
	
	private void closeSocket(BluetoothSocket socket) throws IOException {
		socket.close();
	}

	public void setConnectedThreadPriority(int desiredDeviceThreadPriority) {
		this.mDesiredDeviceThreadPriority = desiredDeviceThreadPriority;
	}

	public int getMaxContiguousConnectionFailures() {
		return mMaxContiguousConnectionFailures;
	}
	
	public void setMaxContiguousConnectionFailures(
			int mMaxContiguousConnectionFailures) {
		this.mMaxContiguousConnectionFailures = mMaxContiguousConnectionFailures;
	}

	public long getConnectionAttemptInterval() {
		return mConnectionAttemptInterval;
	}

	public void setConnectionAttemptInterval(long mConnectionAttemptInterval) {
		this.mConnectionAttemptInterval = mConnectionAttemptInterval;
	}
	

}
