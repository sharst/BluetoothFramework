package de.uos.nbp.senhance.bluetooth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.os.SystemClock;
import android.util.Log;
import de.uos.nbp.Utils;

/**
 * This can be used to maintain a bluetooth
 * connection that is packet-based. It allows sending and receiving of
 * packets and implements escaping (octet stuffing).
 * 
 * It implements a state machine as dictated
 * by {@link PacketConnection}.
 * 
 * The entire packet can be retrieved in one blocking
 * call to receive(), or it can be retrieve one byte at a time
 * with read().
 * 
 * @author rmuil
 * November 18, 2011
 */
public class FramedPacketConnection extends BluetoothPacketConnection {
	private final int mStartByte;
	private final int mEndByte;
	private final int mEscapeByte;
	private final int mOctetStuffByte;
	
	/**
	 * 
	 * @param address Bluetooth MAC address that will be connected to
	 * @param maxPacketSize this is the size of the maximum expected packet on this connection
	 * @param octetStuffByte the byte used to stuff and unstuff a byte after it is escaped (-1 disables)
	 * @param startByte the byte designating the start of a packet
	 * @param endByte the byte designing the end of the packet (must not appear in the data)
	 * @param escapeByte indicates that following character must be treated separately (e.g. octet stuffing)
	 * @param connectRetries The amount of times that attemps to (re)connect should be made
	 * @param timeBetweenConnectionAttemps The time between two connection attemps (in ms)
	 */
	public FramedPacketConnection (String address, PacketConnectionHandler connHandler, int maxPacketSize, 
			int octetStuffByte, int startByte, int endByte, int escapeByte, int connectRetries, int timeBetweenConnectionAttemps) {
		super(address, connHandler, maxPacketSize, connectRetries, timeBetweenConnectionAttemps);
		mOctetStuffByte = octetStuffByte;
		mStartByte = startByte;
		mEndByte = endByte;
		mEscapeByte = escapeByte;
	}
	
	public FramedPacketConnection (String address, PacketConnectionHandler connHandler, int maxPacketSize, 
			int octetStuffByte, int startByte, int endByte, int escapeByte) {
		super(address, connHandler, maxPacketSize, 3, 1000);
		mOctetStuffByte = octetStuffByte;
		mStartByte = startByte;
		mEndByte = endByte;
		mEscapeByte = escapeByte;
	}
	
	public FramedPacketConnection (String address, PacketConnectionHandler connHandler, int maxPacketSize, int octetStuffByte) {
		this(address, connHandler, maxPacketSize, octetStuffByte, DefStartByte, DefEndByte, DefEscapeByte);
	}
	
	public FramedPacketConnection (String address, PacketConnectionHandler connHandler, int maxPacketSize, int connectRetries, int timeBetweenConnectionAttemps) {
		this(address, connHandler, maxPacketSize, DefOctetStuffByte, DefStartByte, DefEndByte, DefEscapeByte, connectRetries, timeBetweenConnectionAttemps);
	}
	
	public FramedPacketConnection (String address, PacketConnectionHandler connHandler, int maxPacketSize) {
		this(address, connHandler, maxPacketSize, DefOctetStuffByte, DefStartByte, DefEndByte, DefEscapeByte);
	}
	public FramedPacketConnection (String address, PacketConnectionHandler connHandler) {
		this(address, connHandler, DefMaxPacketSize, DefOctetStuffByte, DefStartByte, DefEndByte, DefEscapeByte);
	}

	/**
	 * Simply determines if a given byte would
	 * need escaping, given the current connection's
	 * flags.
	 * @param bb
	 * @return true if the byte must be escaped
	 */
	public boolean needsEscaping(int bb) {
		return (((0xFF & bb) == mStartByte) ||
				((0xFF & bb) == mEndByte) ||
				((0xFF & bb) == mEscapeByte));
	}
	
	/**
	 * Processes the next byte and adds it to the current packet
	 * @param nextByte
	 * @throws IOException
	 * @throws ArrayIndexOutOfBoundsException
	 */
	@Override
	protected void readByte (int nextByte){
		//NB: the return from InputStream.read() is actually
		//an 'int' and varies from 0 to 255. 'byte' is signed
		//and varies from -128 to 127.
		
//		try {
			if (D) Log.v(TAG, "FramedPacketConnection|read(): "+Integer.toHexString(nextByte));
			switch (mState) {
			case Ready:
				if (nextByte == mStartByte) {
					mPacket.mStartTime = System.currentTimeMillis();
					mPacket.packetStartMillis = SystemClock.elapsedRealtime();
					changeState(State.Incoming);
				}
				break;
			case Incoming:
				if (nextByte == mEscapeByte) {
					changeState(State.EscapeSequence);
				} else if (nextByte == mEndByte) {
					mPacket.mEndTime = System.currentTimeMillis();
					mPacket.packetEndMillis = SystemClock.elapsedRealtime();
					changeState(State.PacketReceived);
					if (D) {
						Log.d(TAG, "FramedPacketConnection|got packet of length "
								+Integer.toString(mPacket.getDataPosition()));
						Log.v(TAG, " -> [" + Utils.ByteArrayToHexa(mPacket.mData, 0, mPacket.getDataPosition()) + "]");
					}
					
				} else {
					mPacket.appendByte(nextByte);
				}
				break;
			case EscapeSequence:
				if (mOctetStuffByte != -1) {
					/* Apply octet unstuffing */
					nextByte ^= mOctetStuffByte;
				}
				mPacket.appendByte(nextByte);
				changeState(State.Incoming);
				break;
			}
			
			if (mState == State.PacketReceived){
				Packet receivedPacket = mPacket;
				receivedPacket.mPosition = 0;
				mConnHandler.packetReceived(receivedPacket);
				mPacket = new Packet();
				changeState(State.Ready);
				
				
			}
//		} catch (ArrayIndexOutOfBoundsException e) {
//			Log.e(TAG, "Packet arrived that was bigger than FramedPacketConnection buffer. Discarded.");
//			discard();
//		}
	}
	
	/**
	 * This performs necessary escaping on the data, surrounds
	 * it with start and end flags, and sends it to the output
	 * stream.
	 * 
	 * NB: socket is not checked for validity - it is the caller's
	 * responsibility to ensure the connection is Connected before
	 * calling this.
	 */
	@Override
	public void send(Packet pkt) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		if (D) Log.v(TAG, "FramedPacketConnection.send()");
		out.write(mStartByte);
		/* search for bytes requiring escaping... */
		for (int idx = 0; idx < pkt.mData.length; idx++) {
			if (needsEscaping(pkt.mData[idx])) {
				out.write(mEscapeByte);
				int escapedByte = pkt.mData[idx];
				if (mOctetStuffByte != -1)
					escapedByte ^= mOctetStuffByte;
				/*... followed by the massaged data */
				out.write(escapedByte);
			} else {
				out.write(pkt.mData[idx]);
			}
		}
		
		/* finally, send the end byte */
		out.write(mEndByte);
		
		byte[] bytes = out.toByteArray();
		mBluetoothService.write(bytes);

	}
	
}