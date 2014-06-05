package de.uos.nbp.senhance.bluetooth;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.UUID;

import de.uos.nbp.Utils;

/**
 *Provides the definition of a packet-based
 *connection - that is a connection over which
 *entire packets can be received and sent,
 *as opposed to streams.
 *
 * NB: This contains the Packet class, which includes the buffer implementation.
 * It may be more efficient to implement the buffers in the connection class instead.
 * 
 * TODO: Maybe it makes more sense to store mData as an ArrayList<Byte>. This handles
 * the dynamic resizing of the data array for us. 
 * 
 *{@link https://ikw.uni-osnabrueck.de/trac/heartFelt/wiki/Software/Android}
 *
 * @author rmuil@UoS.de
 * November 18, 2011
 */
public interface PacketConnection {

	/*
	 * Defaults are taken from Corscience protocol, but this is convenience
	 * only, not necessary.
	 */
	static final int DefStartByte	= 0xFC;
	static final int DefEndByte		= 0xFD;
	static final int DefEscapeByte	= 0xFE;
	static final int DefOctetStuffByte = 0x20;
	static final int DefMaxPacketSize = 232;
	
	enum State {
		/** Socket has been created and address set, but not yet connected. */
		Disconnected,
		/** Connection established, standing by ready to receive a packet */
		Ready,
		/** A valid packet is coming in */
		Incoming,
		/** Inside escape sequence - an escape flag has been received */
		EscapeSequence,
		/** A full packet has been received. */
		PacketReceived,
		/** The socket has been closed - the connection is dead and cannot be revived. */
		Dead
	}
	
	/**
	 * Packet is similar to a ByteBuffer in that it maintains a backing byte
	 * array and allows reading and writing from it. However, Packet supports
	 * reading and writing of unsigned values as well as signed. It also has
	 * some particulars such as the start and end time of the reception of
	 * the represented packet.
	 * 
	 * The main reason for implementing this as a separate class like this is
	 * to allow sub-classing for specific types of packets.
	 *
	 * @author rmuil@UoS.de
	 * November 24, 2011
	 */
	
	public class Packet {
		/** only affects the getter functions. */
		final boolean mLittleEndian;
		
		
		/** time in milliseconds since Unix epoch of start of packet reception, in Android time */
		long mStartTime;
		/** time in milliseconds since Unix epoch of end of packet reception, in Android time */
		long mEndTime;
		
		 /** System elapsedRealtime after reception of start flag for this packet. */
		long packetStartMillis;
		 /** System elapsedRealtime after reception of last byte of this packet. */
		long packetEndMillis;

		/**
		 * This is the data of the packet.
		 * Would rather keep in an array of integers because bytes
		 * in java are actually signed, so we must be careful about
		 * reading this array to get a an <em>unsigned</em> number (0-255)
		 * rather than -128 to 127.
		 * Stored in bytes because many Stream write() functions
		 * support only byte[] not int[] or short[].
		 */
		protected byte[] mData;
		
		/** This points to the element one past where data has been placed */
		protected int mPosition = 0;
		
		/////
		// Constructors
		/////
		
		Packet (int size, boolean littleEndian) {
			mData = new byte[size];
			mLittleEndian = littleEndian;
		}
		
		public Packet (int size) {
			this(size, true);
		}
		
		Packet () {
			this(DefMaxPacketSize, true);
		}
		
		/**
		 * Copy constructor
		 * @param pkt
		 */
		protected Packet(Packet pkt) {
			this.mLittleEndian= pkt.mLittleEndian;
			this.mData = pkt.mData.clone();
			this.mPosition = pkt.mPosition;
			this.mStartTime = pkt.mStartTime;
			this.mEndTime = pkt.mEndTime;
			this.packetStartMillis = pkt.packetStartMillis;
			this.packetEndMillis = pkt.packetEndMillis;
		}
		
		/**
		 * This allows caller to use their own buffer.
		 * @param data
		 */
		public Packet(byte[] data) {
			mData = data;
			mPosition = data.length;
			mLittleEndian = true;
		}
		
		public int getDataPosition() {
			return mPosition;
		}
		
		public int getRemainingLength(int pos) {
			int remainingLength = getDataPosition() - pos;
			return remainingLength;
		}
		
		public long getStartTime() {
			return mStartTime;
		}
		
		public long getEndTime() {
			return mEndTime;
		}
		
		public byte[] getData() {
			return mData;
		}
		
		public void setData(byte[] data) {
			this.mData = data.clone();
			this.mPosition = data.length;
		}
		
		/////
		// Bytes
		/////
		
		public byte[] getUBytes(int pos, int amount) {
			byte[] buf = new byte[amount];
			for (int ii=0; ii < amount; ii++) {
				buf[ii] = (byte) this.getUByte(pos+ii);
			}
			return buf;
		}
		
		/**
		 * Adds a data byte to current packet buffer
		 * and increments the counter.
		 * @param newByte
		 * @throws ArrayIndexOutOfBoundsException
		 */
		public void appendByte(int newByte) {
			if (mPosition>mData.length-1) mData = enlarge_array(mData, 1);
			mData[mPosition++]=(byte)(newByte&0xFF); 
		}
		
		/**
		 * 
		 * @param newByte
		 * @param pos
		 * @throws ArrayIndexOutOfBoundsException
		 */
		public void putByte(int newByte, int pos) throws ArrayIndexOutOfBoundsException {
			mData[pos]=(byte)(newByte&0xFF);
		}

		public byte getByte(int pos){
			return mData[pos];
		}
		
		public byte popByte() throws ArrayIndexOutOfBoundsException {
			return mData[mPosition++];
		}
		
		
		////
		// Ubytes
		////
		
		/**
		 * 
		 * @param pos
		 * @return unsigned 8-bit integer (unsigned byte)
		 * @throws ArrayIndexOutOfBoundsException
		 */
		public int getUByte(int pos) throws ArrayIndexOutOfBoundsException {
			return mData[pos] & 0xFF;
		}
		
		public int popUByte() {
			return getUByte(mPosition++);
		}
		
		////
		// Shorts
		////

		/**
		 * 
		 * @param pos
		 * @return unsigned 16-bit integer (short)
		 * @throws ArrayIndexOutOfBoundsException
		 */
		public int getUShort(int pos) {
			int ret;
			if (mLittleEndian) {
				ret = (mData[pos]&0xFF)+((mData[pos+1]>>>8)&0xFF);
			} else {
				ret = (mData[pos+1]&0xFF)+((mData[pos]>>>8)&0xFF);
			}
			return ret;
		}
		
		/**
		 * Puts an unsigned 16-bit integer (short) into the current
		 * packet buffer at the requested position.
		 * @param newShort
		 * @param pos
		 * @throws ArrayIndexOutOfBoundsException
		 */
		public void putUShort(int newShort, int pos) throws ArrayIndexOutOfBoundsException {
			if (mLittleEndian) {
				mData[pos] =         (byte) (newShort&0xFF);
				mData[pos+1] = (byte) ((newShort>>>8)&0xFF);
			} else {
				mData[pos+1] =       (byte) (newShort&0xFF);
				mData[pos] =   (byte) ((newShort>>>8)&0xFF);
			}
			if (mPosition < (pos+2))
				mPosition = pos+2;
		}
		

		/**
		 * Returns a short signed integer composed of the 2 bytes
		 * starting at {@value pos}.
		 * 
		 * @param pos
		 * @return short signed integer
		 */
		public short getShort (int pos) {
			int MSB = mLittleEndian ? pos+1:pos;
			int LSB = mLittleEndian ? pos:pos+1;

			return (short) ((((short)mData[MSB]) << 8) | ((short)mData[LSB]) & 0xff);
		}

		
		
		////
		// UInts
		////
		
		/**
		 * Returns an unsigned integer comprised of the {@value size} bytes
		 * in the data from {@value pos}.
		 * 
		 * This is the general case.
		 * 
		 * NB: a long in java is signed and 8 bytes, so an 8 byte
		 * unsigned integer cannot be represented by it 
		 * 
		 * @param pos
		 * @param size
		 * @return unsigned integer
		 * @throws IllegalArgumentException if size is greater than 7.
		 */
		public long getUInt(int pos, int size) {
			long value = 0;
			
			if (size > 7) {
				throw new IllegalArgumentException("long cannot represent an unsigned integer more than 7 bytes long");
			}
			
			for (int ii = 0; ii < size; ii++) {
				if (mLittleEndian) {
					value += (mData[pos+ii] & 0xff) << (8 * ii);
				} else {
					value = (value << 8) + (mData[pos+ii] & 0xff);
				}
			}
			return value;
		}
		
		/**
		 * Returns an unsigned integer constructed of the 4
		 * bytes from {@value pos}.
		 * 
		 * @param pos
		 * @return unsigned integer.
		 */
		public long getUInt(int pos) {
			return getUInt(pos, 4);
		}
		
		public long popUInt() {
			return getUInt(mPosition);
		}
		
		/**
		 * Returns a signed long integer of the 8 bytes starting at pos.
		 * @param pos
		 * @return signed long
		 */
		public long getLong(int pos) {
			long value = 0;
			
			for (int i = 0; i < 8; i++) {
				if (mLittleEndian) {
					value |= ((long)mData[pos+i] & 0xff) << (8 * i);
				} else {
					value = (value << 8) | ((long)mData[pos+i] & 0xff);
				}
			}
			return value;
		}
		
		public String getAscii (int pos, int numBytes) {
			String str = new String();
			for (int ii=0; ii < numBytes; ii++) {
				str += (char)this.getUByte(pos+ii);
			}
			return str;
		}
		
		////
		// Ints
		////
		
		/**
		 * Returns a signed integer composed of 4 bytes
		 * starting at {@value pos}.
		 * @param pos
		 * @return signed integer
		 */
		public int getInt(int pos) throws ArrayIndexOutOfBoundsException {
			int value = 0;
			
			for (int i = 0; i < 4; i++) {
				if (mLittleEndian) {
					value |= ((int)mData[pos+i] & 0xff) << (8 * i);
				} else {
					value = (value << 8) | ((int)mData[pos+i] & 0xff);
				}
			}
			return value;
		}

		
		/**
		 * Puts a signed integer of 4 bytes into the buffer.
		 * @param pos
		 */
		public void putInt (int value, int pos) {
			final int count = 4;
			if (pos+count>mData.length) mData = enlarge_array(mData, (pos+count)-mData.length);
			for (int i = 0; i < count; i++) {
				if (mLittleEndian) {
					mData[pos+i] = (byte) ((value >>> (8 * i))&0xFF);
				} else {
					mData[pos+i] = (byte) ((value >>> (8 * (count-i)))&0xFF);
				}
			}
		}
		
		public void appendInt(int value) {
			putInt(value, mPosition);
			mPosition+=4;
		}
		
		public int popInt() {
			int out = getInt(mPosition);
			mPosition+=4;
			return out;
		}
		
		////
		// Floats
		////

		/**
		 * Puts a single float of 4 bytes into the buffer
		 * @param value
		 * @param pos
		 */
		public void putFloat(float value, int pos) {
			ByteBuffer bb = ByteBuffer.allocate(4);
			if (mLittleEndian) bb.order(ByteOrder.LITTLE_ENDIAN);
			else bb.order(ByteOrder.BIG_ENDIAN);
			bb.putFloat(value);
			
			if ((pos+4)>mData.length) mData = enlarge_array(mData, (pos+4)-mData.length);
			
			for (byte b: bb.array()) {
				mData[pos++] = b;
			}
		}
		
		/**
		 * Decodes 4 bytes starting at position pos into a float
		 * @param pos
		 * @return
		 */
		public float getFloat(int pos) {
			ByteBuffer bb = ByteBuffer.allocate(mData.length);
			if (mLittleEndian) bb.order(ByteOrder.LITTLE_ENDIAN);
			else bb.order(ByteOrder.BIG_ENDIAN);
			bb.put(mData);
			return bb.getFloat(pos);
		}
		
		public void appendFloat(float value) {
			putFloat(value, mPosition);
			mPosition+=4;
		}
		
		public float popFloat() {
			float out = getFloat(mPosition);
			mPosition+=4;
			return out;
		}
		
		////
		// Int arrays
		////
		
		public void putIntArray(int[] values, int pos) {
			// The enlarging would also be done for each int individually, 
			// but this saves (a tiny bit) of time. 
			if ((pos+4*values.length)>mData.length) mData = enlarge_array(mData, (pos+4*values.length)-mData.length);
			
			putInt(values.length, pos);
			for (int i=0; i<values.length; i++) {
				putInt(values[i], pos+4+i*4);
			}
		}
		
		/**
		 * Reads the bytes between start- end endPos and decodes them into ints
		 * @param startPos
		 * @param endPos
		 * @return An array of decoded ints
		 */
		public int[] getIntArray(int pos) {
			int leng = getInt(pos);
			int[] out = new int[leng];
			
			for (int i = 1; i<leng+1; i++) {
				out[i] = getInt(pos+i*4);
			}
			
			return out;
		}
		
		public void appendIntArray(int[] values) {
			putIntArray(values, mPosition);
		}
		
		public int[] popIntArray() {
			int leng = popInt();
			int[] out = new int[leng];
			for (int i = 0; i<leng; i++) {
				out[i] = popInt();
			}
			return out;
		}
		
		////
		// Float arrays
		////
		
		/**
		 * Puts an array of floats into the buffer, starting at position pos
		 * @param values
		 * @param pos
		 */
		public void putFloatArray(float[] values, int pos) {
			putInt(values.length, pos);
			for (int i=0; i<values.length; i++) {
				putFloat(values[i], pos+4+i*4);
			}
		}
		
		/**
		 * Reads the bytes between start- end endPos and decodes them into floats
		 * @param startPos
		 * @param endPos
		 * @return An array of decoded floats
		 */
		public float[] getFloatArray(int pos) {
			int leng = getInt(pos);
			float[] out = new float[leng];
			for (int i=1; i<leng+1; i++) {
				out[i] = getFloat(pos+i);
			}
			return out;
		}
		
		public void appendFloatArray(float[] values) {
			putFloatArray(values, mPosition);
		}
		
		public float[] popFloatArray() {
			int leng = popInt();
			float[] out = new float[leng];
			for (int i=0; i<leng; i++) {
				out[i] = popFloat();
			}
			return out;
		}
		
		 /**
		  * Returns the value of the elapsedRealtime system clock as it was
		  * after reception of the start flag for this packet.
		  * @return packetStartMillis
		  */
		public long getStartMillis() {
			return packetStartMillis;
		}
		/**
		  * Returns the value of the elapsedRealtime system clock as it was
		  * after reception of the <em>last</em> byte of this packet.
		  * @return packetEndMillis
		  */
		public long getEndMillis() {
			return packetEndMillis;
		}
		
		public byte[] enlarge_array(byte[] in, int size) {
			byte[] cop = new byte[in.length+size];
			System.arraycopy(in, 0, cop, 0, in.length);
			return cop;
		}
		
		@Override
		public String toString() {
			//StringBuilder builder = new StringBuilder();
			//builder.append("Packet ");
			//builder.append(Utils.ByteArrayToHexa(mData, mPosition));
			return Utils.ByteArrayToHexa(mData, mData.length);
		}
	}
	
	/**
	 * Attempt to establish a connection based on a service record
	 * identified by the given UUID.
	 * 
	 * @param uuid
	 * @return true on success
	 * @throws IOException 
	 */
	public void connect(UUID uuid) throws IOException;
	
	/**
	 * Attempt to establish the connection directly using.
	 * 
	 * Exceptions are unfortunately necessary to allow use
	 * of low-level workaround in implementation.
	 * 
	 * @return success or failure
	 * @throws NoSuchMethodException 
	 * @throws SecurityException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 * @throws IOException 
	 */
	public void connect(int port) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, IOException;
	
	public void connect() throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, IOException;
	
	/**
	 * This is function relies on the class state. It reads the next byte
	 * from the input stream and processes it according to the
	 * current connection state.
	 * 
	 * If the byte is data, it should be written to the connection's
	 * packet buffer.
	 * 
	 * When a full packet is ready in the buffer, this function must
	 * return true.
	 *
	 * @return whether full packet is ready.
	 * @throws IOException
	 *
	public boolean read() throws IOException;
	
	/**
	 * Blocks until a complete packet has been received.
	 * 
	 * @return the time of the start of the packet
	 *
	public Packet readPacket () throws IOException;
	
	/**
	 * Should discard any data that has already have been received
	 * and revert connection to the <tt>Ready</tt> state (unless currently
	 * disconnected).
	 */
	public void discard();
	
	/**
	 * Sends a packet to the connected host,
	 * must append the start byte, prepend the end byte,
	 * and perform escaping as required.
	 * 
	 * @param pkt the packet to send
	 * @throws IOException
	 */
	public void send (Packet pkt) throws IOException;

	/**
	 * Returns true if the connection is alive. Will be
	 * false if the state is either <tt>Disconnected</tt> or
	 * <tt>Dead</tt>. 
	 * @return true if connection exists.
	 */
	public boolean isConnected ();
	
}