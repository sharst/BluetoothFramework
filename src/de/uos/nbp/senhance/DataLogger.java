package de.uos.nbp.senhance;

import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An DataLogger is used to persistently store data and events.
 * 
 * <p>
 * Each logger has at least one output stream. They may optionally
 * support arbitrarily more output streams. Each stream is named, which
 * is used for the status string and for directed output.
 * </p>
 * 
 * NB: It is important here to ensure that writes are atomic.
 * Multiple threads may use this, and a single call to a write function
 * <em>must</em> result in a contiguous output in the
 * data, otherwise data may be interleaved resulting in a corrupt file.
 *
 * 
 * <ul>
 * <li>This is both a 'data' and an 'event' logger. TODO: factor these out, perhaps.
 * <li>TODO: create 2 classes: a DataLogController and a DataLogger, where the Controller
 * allows closing of the logs and the logger just allows adding to existing? (see close())
 * </ul>
 * 
 * @author rmuil@UoS.de
 */
public interface DataLogger {
	
	 /**
	  * This should prepare the underlying mechanism for logging,
	  * opening the requested streams for writing.
	  * 
	  * @return a map of the named streams
	  */
	ConcurrentHashMap<String,OutputStream> prepare(HashMap<String,String> streamDetails);
	
	/**
	 * This should return a string describing the status of the DataLogger.
	 * 
	 * @return a string describing which streams are open
	 */
	String getStatusString ();

	/**
	 * Should write a byte array to all open log streams.
	 */
	void write (byte [] bytes);
	
	/**
	 * Should write specified part of a byte array to all open log streams.
	 */
	void write (byte [] bytes, int offset, int count);
	
	/**
	 * Should write the string to all open log streams.
	 */
	void write (String string);
	
	/**
	 * Should write byte array directly to a specified stream.
	 *
	 * @param streamName - name of the stream to which to write
	 * @param bytes the byte array containing the bytes to write
	 */	
	void write (String streamName, byte [] bytes);
	
	/**
	 * Should write certain bytes of an array directly to a specified stream.
	 *
	 * @param streamName - the stream to which to write
	 * @param bytes - the byte array containing the bytes to write
	 * @param offset - the offset into the byte array at which to start
	 * @param count - the number of bytes to write from offset
	 */
	void write (String streamName, byte [] bytes, int offset, int count);
	
	/**
	 * Should write a String directly to a specified stream.
	 *
	 * @param streamName - name of the stream to which to write
	 * @param string the String to write
	 */	
	void write (String streamName, String string);
	
	/**
	 * @deprecated since DataVersion 11, use {@link writeEvent} instead.
	 */
	void writeDate (String description);
	
	/**
	 * @deprecated since DataVersion 11, use {@link writeEvent} instead.
	 */
	void writeDate (String description, Date theDate);
	
	/**
	 * This should write an 'event' (side-band information as opposed
	 * to 'data') to all open streams. An event consists of:
	 * <ul>
	 *  <li> name
	 *  <li> primaryDate
	 *  <li> secondaryDate
	 *  <li> content
	 * </ul>
	 * @param name - e.g. <tt>ExPC</tt> or <tt>UserEvent</tt>
	 * @param primaryDate - time event occurred, according to the destination device
	 * @param secondaryDate - time event occurred according to source device (optional, can be null)
	 * @param content - this is the text of the event and can be anything
	 */
	void writeEvent (String name, Date primaryDate, Date secondaryDate, String content);
	
	/**
	 * Same as writeEvent(String, Date, Date, String) but just adds ability to 
	 * take content from existing byte array.
	 * 
	 * @see writeEvent (String, Date, Date, String)
	 * @param name
	 * @param primaryDate
	 * @param secondaryDate
	 * @param content array of bytes from which to take content (optional, may be null)
	 * @param contentOffset offset into content byte array where the event content starts
	 * @param contentSize how many bytes, from offset, of the content byte array to write
	 */
	void writeEvent (String name, Date primaryDate, Date secondaryDate, byte [] content, int contentOffset, int contentSize);

	/**
	 * Close all open streams.
	 * 
	 * Actually, clients of the DataLogger should not call this, because it
	 * effects all of those who have a copy of the instance. Would be better
	 * to allow some sort of control instance which <em>can</em> call this, whereas
	 * the standard DataLogger interface cannot.
	 * 
	 * For now, just know: as a user of the DataLogger, don't call this.
	 */
	void close();
}