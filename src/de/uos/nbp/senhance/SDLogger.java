/**
 * 
 */
package de.uos.nbp.senhance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;

/**

 * 
 * A Logger that writes to SD card in CSV format.
 * 
 * <p>
 * TODO: the log class should monitor for an appropriate message
 * that the SD card is no longer available and other such relevant
 * things.
 * </p>
 * 
 * @author rmuil@UoS.de
 */
public class SDLogger implements DataLogger {
	private static final String TAG = "Senhance";
	@SuppressWarnings("unused")
	private static final boolean D = true;
	
	/**
	 * The version of the data-file used.
	 * 
	 * <ul>
	 *  <li> 0.7 - first. very basic.
	 *  <li>0.7.1 - using elapsedRealtime, and basing clockMapping on mean of clockOffsets rather than minimum. samples outside of standard deviation are not taken into account.
	 *  <li>0.7.2
	 *  <li>0.7.3
	 *  <li>0.8.0 - store date strings in all logs (completely revised threading model)
	 *  <li>0.9.0 - using AudioTrack for generation of HeartOut sounds. no change to data storage format.
	 *  <li>0.9.2 - no change to format, but now subject name is added to logs if available
	 *  <li>0.9.5 - packet reception times are now in BPM file: no sense writing the same time for each frame. also, no signal-detect any more.
	 *  <li>0.9.6 - UserEvents now exist. logging has been factored out to SDLogger class, but this is an implementation detail
	 *  <li>10    - data format version now distinct from app version and a simple number. no need for anything else.
	 *  <li>11    - now have ExPC type event log, have introduced the writeEvent function to standardize the event logs.
	 * </ul>
	 */
	public static final String DataVersion = "11";
	
	private final static SimpleDateFormat fileNameFormatter =
			new SimpleDateFormat("yyyy-MM-dd'T'HH.mm.ss", Locale.US);
	private final static SimpleDateFormat fullDateFormatter =
			new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US);
	private static final byte[] EventDesignatorBytes = "#".getBytes();
	private static final byte[] EventSectionSeparatorBytes = "|".getBytes();
	private static final byte[] DataSeparatorBytes = ",".getBytes();
	private static final byte[] LineEndBytes = "\n".getBytes();
	

	/**
	 * Replacement for the String.getBytes() function.
	 * 
	 * The getBytes() function is quite slow because it performs correct
	 * character encoding, character by character. This function assumes
	 * ASCII character set. Each character is mapped directly to a single byte.
	 * 
	 * So long as the character encoding is ASCII, this is sufficient and
	 * much faster than getBytes().
	 * 
	 * Taken from <a href="http://www.javacodegeeks.com/2010/11/java-best-practices-char-to-byte-and.html">www.javacodegeeks.com</a>.
	 */
	public static byte[] stringToBytesASCII(String str) {
		byte[] b = new byte[str.length()];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) str.charAt(i);
		}
		return b;
	}
	
	
	final String mAndroidID;
	final String mAndroidIMEI;
	final String mModel;
	final String mManufacturer;
	final String mProduct;

	final int mBuildVersionSDK;
	
	String mAndroidSerial;

	ConcurrentHashMap<String,OutputStream> streams;

	@TargetApi(9)
	public SDLogger(Context context) {
		Log.v(TAG, "SDLogger()");
		streams = new ConcurrentHashMap<String,OutputStream>(3,(float) 1.0,2);
		
		mAndroidID = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
		Log.i(TAG, "ANDROID_ID="+mAndroidID);

		TelephonyManager mTelephonyMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		mAndroidIMEI = mTelephonyMgr.getDeviceId(); // Requires READ_PHONE_STATE
		Log.i(TAG, "IMEI="+mAndroidIMEI);

		mBuildVersionSDK = Build.VERSION.SDK_INT;
		if (mBuildVersionSDK <= Build.VERSION_CODES.FROYO) {
			//android.os.Build.SERIAL requires API 9 (Android 2.3) or later unfortunately
			//http://developer.android.com/reference/android/os/Build.html#SERIAL
			try {
				Class<?> c = Class.forName("android.os.SystemProperties");
				Method get = c.getMethod("get", String.class);
				mAndroidSerial = (String) get.invoke(c, "ro.serialno");
			} catch (Exception ignored) {
				mAndroidSerial = null;
			}
		} else {
			mAndroidSerial = SDLogger.getHardwareSerial();
		}
		if (mAndroidSerial != null)
			Log.i(TAG, "Serial="+mAndroidSerial);

		mManufacturer = Build.MANUFACTURER;
		mModel = Build.MODEL;
		mProduct = Build.PRODUCT;
		
		//String phoneNumber=mTelephonyMgr.getLine1Number(); // Requires READ_PHONE_STATE
		//String softwareVer = mTelephonyMgr.getDeviceSoftwareVersion(); // Requires READ_PHONE_STATE
		// String simSerial = mTelephonyMgr.getSimSerialNumber(); // Requires READ_PHONE_STATE
		//String subscriberId = mTelephonyMgr.getSubscriberId(); // Requires READ_PHONE_STATE
		//Log.i(TAG, "Build.PRODUCT="+Secure.getString(getContentResolver(), Build.PRODUCT));
	}

	/**
	 * This attempts to ensure that the requested streams are open.
	 * 
	 * <p>
	 * It can be called multiple times - if the streams are already open, this
	 * will have no effect.
	 * 
	 * The input map keys are the stream names, and the values are the headers of 
	 * the columns of the CSV data.
	 * </p>
	 * @return a HashMap containing the output streams, with their names as keys.
	 */
	@Override
	synchronized public ConcurrentHashMap<String,OutputStream> prepare(HashMap<String,String> streamDetails) {

		Log.v(TAG, "SDLogger.prepare()");
		String state = Environment.getExternalStorageState();	
		if (!Environment.MEDIA_MOUNTED.equals(state)) {
			//we cannot read and read
			Log.e(TAG, "SD card is not mounted");
			if (!streams.isEmpty()) {
				//TODO: close and null all streams
			}
			return null;
		}
		
		File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		Log.v(TAG, "Log directory="+ path.getPath());

		try {
			if (!path.exists()) {
				if (!path.mkdirs()) {
					Log.e(TAG, "Failed to create log directory.");
					return null;
				}
			}
		} catch (SecurityException e) {
			Log.e(TAG, "Failed for security reasons to create log directory.", e);
			return null;
		} 
		
		Date now = new Date();
		final String nowFileStr = fileNameFormatter.format(now);
	
		final String fileOpenStr = "#fileOpen="+fullDateFormatter.format(now)+"\n";
		final String dataVersionStr = "#heartFeltDataVersion="+DataVersion+"\n";
		final String androidIDStr = "#androidID="+mAndroidID+"\n";
		final String sdkStr = "#androidSDK="+mBuildVersionSDK+"\n";
		final String manufacturerStr = "#androidManufacturer="+mManufacturer+"\n";
		final String modelStr = "#androidModel="+mModel+"\n";
		final String productStr = "#androidProduct="+mProduct+"\n";
		final String imeiStr = "#IMEI="+mAndroidIMEI+"\n";
		final byte [] serialStrBytes;
		StringBuilder serialStr = new StringBuilder("#Serial=");
		if (mAndroidSerial != null) {
			if (mAndroidSerial.length() > 0) {
				serialStr.append(mAndroidSerial);
			}
		}
		serialStr.append(";\n");
		serialStrBytes = serialStr.toString().getBytes();
		
		for (Entry<String,String>streamDetail: streamDetails.entrySet()) {
			
			String name = streamDetail.getKey();
			String headers = streamDetail.getValue();
			/*
			 * NB: this method *must* be synchronized because of the following
			 * disparity between checking for a key, and adding a new key.
			 * If this was made atomic or synchronized at a low level, then
			 * the method could in principle be made unsynchronized, but this isn't
			 * advisable because running more than 1 prepare method at a time doesn't
			 * make sense anyway.
			 */
			OutputStream strm = null;
			if (streams.containsKey(name)) {
				strm = streams.get(name);
				
				/* check that the stream is still ok? */
				try {
					strm.flush();
				} catch (IOException ignored) {
					Log.w(TAG, "Stream '"+name+"' closed, reopening...");
					streams.remove(name);
					strm = null;
				}
			}
			
			if (strm == null) {
				File logFile = new File(path, nowFileStr+"-"+name+".csv");
				try {
					strm = new FileOutputStream(logFile, true);
					strm.write(("#"+name+" data file\n").getBytes());
					strm.write(("#"+headers+"\n").getBytes());
					strm.write(fileOpenStr.getBytes());
					strm.write(dataVersionStr.getBytes());
					strm.write(androidIDStr.getBytes());
					strm.write(sdkStr.getBytes());
					strm.write(manufacturerStr.getBytes());
					strm.write(productStr.getBytes());
					strm.write(modelStr.getBytes());
					strm.write(imeiStr.getBytes());
					strm.write(serialStrBytes);
				} catch (FileNotFoundException e) {
					Log.e(TAG, logFile + " not found.");
				} catch (IOException e) {
					Log.e(TAG, "I/O exception on "+logFile + ".");
					strm = null;
				} 

				if (strm != null) {
					Log.v(TAG, name+" log stream opened and ready: "+logFile.getPath());
					streams.put(name, strm);
				}
			}
			
		}

		return streams;
	}
	
	/**
	 * Returns a comma-separated list of the names of all
	 * open streams or "inactive" if none are active.
	 */
	@Override
	public String getStatusString() {
		StringBuilder str = new StringBuilder();
		
		for (Entry<String, OutputStream> strmEntry : streams.entrySet()) {
			if (str.length() != 0) str.append(",");
			str.append(strmEntry.getKey());
		}
		if (str.length() == 0) {
			str.append("inactive");
		}
		return str.toString();	
	}
	
	/**
	 * This is the workhorse of the SDLogger class. Here is
	 * where the data is actually written to the streams.
	 * 
	 * @param stream the stream to which to write
	 * @param bytes the byte array containing the bytes to write
	 * @param offset the offset into the byte array at which to start
	 * @param count the number of bytes to write from offset
	 * @return success or failure
	 */
	private boolean write(OutputStream stream, byte [] bytes, int offset, int count) {
		try {
			synchronized(stream) {
				stream.write(bytes, offset, count);
			}
		} catch (IOException e) {
			Log.e(TAG, "OutputStream.write() failed: " + e);
			stream = null;
			return false;
		}
		return true;
	}
	
	/**
	 * Write specified bytes directly to a named stream.
	 */
	@Override
	public void write(String streamName, byte [] bytes, int offset, int count) {
		OutputStream strm;
		strm = streams.get(streamName);
		
		if (strm != null) {
			if (!this.write(strm, bytes, offset, count)) {
				Log.e(TAG, "Write to stream "+streamName+" failed, removing from list.");
				streams.remove(streamName);
			}
		}
	}

	/**
	 * Writes all bytes directly to a named stream.
	 */
	@Override
	public void write(String streamName, byte[] bytes) {
		this.write(streamName, bytes, 0, bytes.length);
	}

	/**
	 * Write an ASCII String directly to a named stream.
	 * <em>NB: This will only work with ASCII strings. </em>
	 */
	@Override
	public void write(String streamName, String string) {
		this.write(streamName, stringToBytesASCII(string));
	}

	
	
	/**
	 * Write specified part of given byte array to all open log streams.
	 */
	@Override
	public void write(byte[] bytes, int offset, int count) {
		for (Iterator<Entry<String,OutputStream>> el = streams.entrySet().iterator(); el.hasNext(); ) {
			Entry<String,OutputStream> entry = el.next();
			if (!this.write(entry.getValue(), bytes, offset, count)) {
				Log.e(TAG, "Write to stream "+entry.getKey()+" failed, removing from list.");
				el.remove();
			}
		}
	}

	/**
	 * Write the entire byte array to all open log streams.
	 */
	@Override
	public void write(byte [] bytes) {
		this.write(bytes, 0, bytes.length);
	}
	
	/**
	 * Write the given string to all open log streams.
	 */
	@Override
	public void write(String str) {
		this.write(stringToBytesASCII(str));
	}
	
	/**
	 * @deprecated since DataVersion 11, use {@link writeEvent(String, Date)} instead.
	 */
	@Override
	public void writeDate(String description, Date theDate) {
		StringBuilder dateStr = new StringBuilder();
		dateStr.append("#");
		dateStr.append(description);
		dateStr.append("=");
		dateStr.append(fullDateFormatter.format(theDate));
		dateStr.append("\n");
		this.write(stringToBytesASCII(dateStr.toString()));
	}
	
	/**
	 * @deprecated since DataVersion 11, use {@link writeEvent(String)} instead.
	 */
	@Override
	public void writeDate(String description) {
		this.writeDate(description, new Date());
	}

	/**
	 * Closes all streams and empties the HashMap.
	 */
	@Override
	public void close() {
		for (Iterator<Entry<String,OutputStream>> el = streams.entrySet().iterator(); el.hasNext(); ) {
			Entry<String,OutputStream> entry = el.next();
			try {
				entry.getValue().close();
			} catch (IOException ignored) {
				Log.w(TAG, "Warning, got ioexception closing '"+entry.getKey()+"' stream.");
			}
			el.remove();
		}		
	}

	

	/**
	 * This writes an event, as defined by name, content, and 1 or 2 dates, to
	 * the open streams according to the standard format.
	 * 
	 * This uses a new byte buffer for every call. Probably not the most efficient,
	 * but anything else will require careful synchronization because this
	 * must be thread safe.
	 */
	@Override
	public void writeEvent(String name, Date primaryDate, Date secondaryDate,
			byte[] content, int contentOffset, int contentSize) {
		final int InitialEventSize = 300;
		ByteBuffer eventBuf = ByteBuffer.allocate(InitialEventSize );
		
		eventBuf.clear();
		
		eventBuf.put(EventDesignatorBytes);
		eventBuf.put(stringToBytesASCII(fullDateFormatter.format(primaryDate)));
		if (secondaryDate != null) {
			eventBuf.put(DataSeparatorBytes);
			eventBuf.put(stringToBytesASCII(fullDateFormatter.format(secondaryDate)));
		}
		eventBuf.put(EventSectionSeparatorBytes);
		
		eventBuf.put(stringToBytesASCII(name));
		
		if (content != null) {
			eventBuf.put(EventSectionSeparatorBytes);
			eventBuf.put(content, contentOffset, contentSize);
		}
		eventBuf.put(LineEndBytes);
		this.write(eventBuf.array(), eventBuf.arrayOffset(), eventBuf.position());
	}
	
	/**
	 * This writes an event, as defined by name, content, and 1 or 2 dates, to
	 * the open streams according to the standard format.
	 * 
	 * This uses a new byte buffer for every call. Probably not the most efficient,
	 * but anything else will require careful synchronization because this
	 * must be thread safe.
	 */
	@Override
	public void writeEvent(String name, Date primaryDate, Date secondaryDate,
			String content) {
		byte [] contentBytes = stringToBytesASCII(content);
		writeEvent(name, primaryDate, secondaryDate, contentBytes, 0, contentBytes.length);
	}
	
	/** TODO: move to some utils class */
	private static String getHardwareSerial() {
	    try {
	        Field serialField = Build.class.getDeclaredField("SERIAL");
	        return (String)serialField.get(null);
	    }catch (Exception nsf) {
	    	//nop
	    } 

	    return Build.UNKNOWN;
	}

}