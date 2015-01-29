package de.uos.nbp.senhance.bluetooth;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.UUID;

import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

public class BluetoothPacketConnection extends Handler implements PacketConnection {

	protected static final String TAG = "heartFelt";
	protected static final boolean D = false;
	protected final int mMaxPacketSize;
	protected PacketConnectionHandler mConnHandler;
	protected State mState;
	protected Packet mPacket;
	protected String mAddress;
	protected BluetoothService mBluetoothService;

	/**
	 * Creates a new bluetooth connection
	 * @param address The address of the device to connect to 
	 * @param connHandler A handler used to communicate status info
	 * @param maxPacketSize The maximum size of packets to be sent over this connection
	 * @param connectRetries The amount of times that attemps to (re)connect should be made
	 * @param timeBetweenConnectionAttemps The time between two connection attemps (in ms)
	 */
	public BluetoothPacketConnection(String address,
			PacketConnectionHandler connHandler, int maxPacketSize, int connectRetries, int timeBetweenConnectionAttemps) {
		if (D) Log.v(TAG, "BluetoothConnection()");
		mConnHandler = connHandler;
		mMaxPacketSize = maxPacketSize;
		mAddress = address;
		mBluetoothService = new BluetoothService(this); /* TODO: rmuil: this looks bad to me, like could be a memory leak. */
		mState = State.Disconnected;
		mBluetoothService.setMaxContiguousConnectionFailures(connectRetries);
		mBluetoothService.setConnectionAttemptInterval(timeBetweenConnectionAttemps);
	}
	

	/**
	 * This uses a workaround for the failure of 
	 * device.createRfcommSocketToServiceRecord(MY_UUID);
	 * Calls the BluetoothDevice.createRfcommSocket(int channel) function which is,
	 * for some reason, hidden.
	 * @see <a href="https://ikw.uni-osnabrueck.de/trac/heartFelt/wiki/Software/Android#BluetoothSockets">BluetoothSockets</a>
	 * @throws NoSuchMethodException 
	 * @throws SecurityException 
	 * @throws SecurityException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 * @throws IOException 
	 */
	@Override
	public void connect(int port) {
		mBluetoothService.connect(mAddress, port);
	}
	

	/**
	 * Connects to the default RFCOMM channel (1).
	 * 
	 * @throws IOException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 * @throws NoSuchMethodException 
	 * @throws IllegalArgumentException 
	 * @throws SecurityException 
	 */
	@Override
	public void connect() {
		connect(1);
	}

	/**
	 * TODO: Is this function still needed?
	 * 
	 * This creates a socket the normal way - using a UUID record.
	 * It does not work on the HTC Desire. Use {@link connect(int)} in
	 * this case.
	 * 
	 * @throws IOException 
	 */
	@Override
	public void connect(UUID uuid) {
		if (D) Log.v(TAG, "BluetoothConnection.connect(uuid)");
		mBluetoothService.connect(mAddress, uuid);
	}

	protected void changeState(State newState) {
		if (D) Log.v(TAG, "BluetoothConnection|"+mState+"->"+newState);
		mState = newState;
	}

	public boolean isConnected() {
		return ((mState != State.Disconnected) && (mState != State.Dead));
	}

	public void setConnectedThreadPriority(int DesiredDeviceThreadPriority) {
		mBluetoothService.setConnectedThreadPriority(DesiredDeviceThreadPriority);
	}

	public void flush() {
		mBluetoothService.flush();
	}

	public void close() {
		mBluetoothService.stop(); //socket.close
		changeState(State.Disconnected);
	}

	public String getName() {
		return mBluetoothService.getDeviceName();
	}

	public String getAddress() {
		return mAddress;
	}

	public long getConnectionAttemptInterval() {
		return mBluetoothService.getConnectionAttemptInterval();
	}
	
	public int getmMaxContiguousConnectionFailures() {
		return mBluetoothService.getMaxContiguousConnectionFailures();
	}

	public void setMaxContiguousConnectionFailures(
			int mMaxContiguousConnectionFailures) {
		mBluetoothService
				.setMaxContiguousConnectionFailures(mMaxContiguousConnectionFailures);
	}

	public void setConnectionAttemptInterval(long mConnectionAttemptInterval) {
		mBluetoothService
				.setConnectionAttemptInterval(mConnectionAttemptInterval);
	}

	/**
	 * If connected, discards any data that has already been received
	 * and reverts connection to the <tt>Ready</tt> state.
	 * 
	 * If not connected, does nothing.
	 */
	@Override
	public void discard() {
		if (isConnected()) {
			mPacket = new Packet(mMaxPacketSize);
			changeState(State.Ready);
		}
	}

	@Override
	public void handleMessage(Message msg) {
	        
			switch (msg.what) {
			case BluetoothService.CONNECT_FAILED:
				handleConnectFailed(msg);
				break;
			case BluetoothService.CONNECTION_LOST:
				mConnHandler.connectionLost(msg.getData().getString(BluetoothService.MESSAGE));
				changeState(State.Disconnected);
				break;
			case BluetoothService.CONNECT_ATTEMPT_FAILED:
				mConnHandler.connectAttemptFailed(msg.getData().getString(BluetoothService.MESSAGE));
				changeState(State.Disconnected);
				break;
			case BluetoothService.CONNECTION_CLOSED:
				mConnHandler.connectionClosed();
				changeState(State.Disconnected);
				break;
			case BluetoothService.MESSAGE_STATE_CHANGE:
	//			Context context1 = getApplicationContext();
				switch (msg.arg1) {
				case BluetoothService.STATE_CONNECTED:
					handleConnectedMessage(msg);
					break;
				case BluetoothService.STATE_CONNECTING:
					break;
				case BluetoothService.STATE_LISTEN:
					break;
				case BluetoothService.STATE_NONE:
					break;
				}
				break;
			case BluetoothService.MESSAGE_READ:
	            readByte(msg.arg1);
			}
		}

	private void handleConnectFailed(Message msg) {
		String message = msg.getData().getString(BluetoothService.MESSAGE);
		Log.w(TAG, "BluetoothConnection|socket.connect() failed: "+message);
		changeState(State.Disconnected);
		mConnHandler.connectFailed(message);
	}

	/**
	 * Puts the byte in a packet and notifies the handler. Here 1 Byte = 1 Packet.
	 * TODO: rmuil: the timestamps and the name of this function have lost their
	 *   meaning in the whole refactoring. the timestamps are essentially useless. 
	 */
	protected void readByte (int nextByte){
		mPacket.mStartTime = System.currentTimeMillis();
		mPacket.packetStartMillis = SystemClock.elapsedRealtime();
		mPacket.appendByte(nextByte);
		mConnHandler.packetReceived(mPacket);
		mPacket.mEndTime = System.currentTimeMillis();
		mPacket.packetEndMillis = SystemClock.elapsedRealtime();
		mPacket = new Packet(mMaxPacketSize);
	}
	
	private void handleConnectedMessage(Message msg) {
		mPacket = new Packet(mMaxPacketSize);
		changeState(State.Ready);
		mConnHandler.connected();
	}

	@Override
	public void send(Packet pkt) throws IOException {
		if (D) Log.v(TAG, "BluetoothPacketConnection.send()");
		if ((mState!=State.Disconnected) && (mState != State.Dead)) {
			mBluetoothService.write(pkt.getData());
		} else {
			throw new IOException("BluetoothConnection is not active at the moment!");
		}
	}

}