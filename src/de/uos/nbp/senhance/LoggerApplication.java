package de.uos.nbp.senhance;

import android.app.Application;

/**
 * This application can be used to provide a single EventLogger.
 *
 * @author rmuil
 * November 30, 2011
 */
public class LoggerApplication extends Application {

	private Object logLock = new Object();
	private volatile DataLogger applicationLogger;
	
	public DataLogger getLogger() {
		synchronized(logLock) {
			if (applicationLogger == null) {
				applicationLogger = new SDLogger(getApplicationContext());
			}
		}
		return applicationLogger;
	}
	
	public void setLogger (DataLogger applicationLogger) {
		synchronized(logLock) {
			this.applicationLogger = applicationLogger;
		}
	}
}
