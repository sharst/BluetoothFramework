package de.uos.nbp;

import java.util.Date;

/**
 * Miscellaneous Java functions that may be of utility
 * to anyone in the NBP group.
 * 
 * @author rmuil@UoS.de
 */
public class Utils {
	public static final double MsecsInMin = 60.0*1000.0;
    
	private static final char SeparatorChar = '|';
	
    static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

	/* static helper functions */
	public static String ByteArrayToHexa(byte[] a) {
		return ByteArrayToHexa(a, 0, a.length);
	}
	public static String ByteArrayToHexa(byte[] a, int nbytes) {
		return ByteArrayToHexa(a, 0, nbytes);
	}
	public static String ByteArrayToHexa(byte[] a, int offset, int nbytes) {

	    if (nbytes > (a.length-offset))
	    	nbytes = (a.length-offset);
	    
		StringBuilder buf = new StringBuilder(nbytes*3+2); /* each byte is 2 characters plus a separator */
		
		for (int ii = offset; ii < offset+nbytes; ii++) {
			//buf.append(ByteToHexa(a[ii]));
			buf.append(HEX_CHARS[(a[ii] & 0xF0) >>> 4]);
			buf.append(HEX_CHARS[ a[ii] & 0x0F]);
	        
			if (ii < offset+nbytes-1)
				buf.append(SeparatorChar);
		}
		
		return buf.toString();
	}
	public static char[] ByteToHexa(byte a) {
		char[] buf = new char[2];
		
		buf[0]=HEX_CHARS[(a & 0xF0) >>> 4];
		buf[1]=HEX_CHARS[ a & 0x0F];
		
		return buf;
	}
	
	/**
	 * @see <a href="http://stackoverflow.com/questions/2517709/java-comparing-two-dates-to-see-if-they-are-in-the-same-day">StackOverflow: java-comparing-two-dates-to-see-if-they-are-in-the-same-day</a>
	 */
	public static boolean isSameDay(Date date1, Date date2) {

		long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;
	    // Strip out the time part of each date.
	    long dayNumber1 = date1.getTime() / MILLIS_PER_DAY;
	    long dayNumber2 = date2.getTime() / MILLIS_PER_DAY;

	    // If they now are equal then it is the same day.
	    return dayNumber1 == dayNumber2;
	}
	
	
	/**
	 * Converts between beats-per-minute and R-R interval in milliseconds.
	 * @param bpmOrRR either beats-per-minute or an R-R interval in msecs
	 * @return the corresponding R-R interval in msecs or beats-per-minute
	 */
	public static float bpmRR(float bpmOrRR) {
		return (float)MsecsInMin/bpmOrRR;
	}
	
	/**
	 * Really simple, just increments the given current variable toward desired
	 * by the given increment.
	 * Sets to 0 if within increment to avoid oscillating around desired.
	 * @param current - in arbitrary units
	 * @param desired - in arbitrary units
	 * @param inc - in units per second
	 * @param timeDelta - time since last set in milliseconds
	 * @return
	 */
	public static float smoothSet (float current, float desired, float inc,
			long timeDelta) {
		
		/* determine how much increment is needed for the time delta */
		inc *= ((float) timeDelta)/1000.0;
		if (inc < 0)
			inc = -inc;
		
		float diff = current - desired;
		if (diff != 0) {
			if (diff > 0) {
				if (diff < inc)
					current = desired;
				else
					current -= inc;
			}
				
			if (diff < 0) {
				if (-diff < inc)
					current = desired;
				else
					current += inc;
			}
		}
		return current;
	}
}
