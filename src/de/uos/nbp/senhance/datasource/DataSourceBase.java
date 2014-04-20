package de.uos.nbp.senhance.datasource;

import java.util.concurrent.CopyOnWriteArrayList;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import de.uos.nbp.senhance.DataLogger;

public abstract class DataSourceBase extends Thread implements DataSource {
	
	private boolean debug = false;
	protected static final String TAG = "Senhance";
	private final static long MaximumThreadCloseTime = 200;
	
	private final static long PausedSleepTime = 200;
	
	private int numEventSinks = 0;
	private CopyOnWriteArrayList<DataSink> mEventSinks = null;
	
	private Handler mHandler = null;
	protected DeviceState mState = DeviceState.Disconnected;
	protected int mID = -1;
	
	protected DataLogger mDataLogger = null;
	
	private boolean transmissionAutoStart = false;
	private boolean paused = true;
	private boolean thread_should_die = false;
	
	public DataSourceBase() {
		mEventSinks = new CopyOnWriteArrayList<DataSink>();
		
		try {
			this.setPriority(DefaultThreadPriority);
		} catch (SecurityException e) {
			Log.w(TAG, "Got SecurityException trying to set device thread priority: "+e);
		} catch (IllegalArgumentException e1) {
			Log.e(TAG, "oops, gave the wrong priority: "+e1);
		}

		Log.i(TAG, "SourceThread|ThreadPriority="+this.getPriority());
		
	}
	/**
	 * 
	 * 
	 * @param time
	 * @param dataEventPresent
	 * @param data
	 */
	protected void triggerSinks(long time, boolean dataEventPresent, int data) {
		synchronized(mEventSinks) {
			/*
			 * Iteration over the array list is done manually with an integer
			 * to avoid the high overhead of creating an Iterator every time.
			 * This function must be very efficient.
			 */
			for (int idx = 0; idx < numEventSinks; idx++) {
				mEventSinks.get(idx).trigger(time, dataEventPresent, data);
			}
		}
	}
	
	@Override
	public void attachLogger(DataLogger eventLogger) {
		if (debug) Log.d(TAG, "DummySourceThread.attachLogger()");
		mDataLogger = eventLogger;
		prepareStorage();
	}

	/**
	 * This should ensure that the event logger is
	 * configured correctly and data is ready to be
	 * logged.
	 * @return success
	 */
	public abstract boolean prepareStorage();
	
	@Override
	public void attachSink(DataSink newEventSink) {
		
		newEventSink.resetClockMapping();
		newEventSink.updateClockMapping(0,0);
		mEventSinks.add(newEventSink); /* does not need to be synchronized */
		synchronized(mEventSinks) {
			numEventSinks = mEventSinks.size();
		}
	}

	@Override
	public void detachSink(DataSink sinkToDetach) {
		synchronized(mEventSinks) {
			mEventSinks.remove(sinkToDetach);
			numEventSinks = mEventSinks.size();
		}
	}

	@Override
	public boolean isSinkAttached () {
		return (!mEventSinks.isEmpty());
	}
	
	/**
	 *  Currently only supports single handler. Adding a second will override first.
	 *  
	 */
	@Override
	public void attachUIHandler(Handler handler, int eventSourceID) {
		mHandler = handler;	
		mID  = eventSourceID;
	}


	/**
	 * 
	 * @return the sourceID passed in {@link attachUIHandler}.
	 */
	@Override
	public int getSourceID () {
		return mID;
	}
	
	/**
	 * Simply removes existing handler. input parameter is ignored.
	 * 
	 */
	@Override
	public void detachUIHandler(Handler handler) {
		mHandler = null;
		mID = -1;
	}
	/**
	 * 
	 */
	@Override
	public void connect(String address) {
		changeState(DeviceState.Connecting);
		prepareStorage();
		if (!super.isAlive())
			super.start(); /* call thread start() */
	}
	
	public void connect(String address, boolean autoStart) {
		transmissionAutoStart = autoStart;
		connect(address);
	}

	/**
	 * Stops data transmission.
	 */
	@Override
	public void disconnect() {
		stopEvents();
		thread_should_die = true;
		try {
			super.join(MaximumThreadCloseTime);
		} catch (InterruptedException ignored) {
		}
	}

	/**
	 * 
	 */
	@Override
	public DeviceState getSourceDeviceState() {
		return mState;
	}

	/**
	 * @return false
	 */
	@Override
	public boolean needBluetooth() {
		return false;
	}
	
	/**
	 * This returns null because dummy sources don't have an underlying
	 * device that needs finding or filtering.
	 * @return null
	 */
	@Override
	public String getDeviceNameFilterString() {
		return null;
	}
	
	
	/**
	 * Helper function to send a message to the attached UI handler. 
	 */
	protected void sendUIMessage(UIEvent ev, int arg) {
		if (mHandler != null) {
			mHandler.obtainMessage(ev.ordinal(), mID, arg).sendToTarget();
		}
	}
	
	/**
	 * Helper function to use when state changes - 
	 * just updates the state variable and sends a message
	 * indicating the state change to the UI handler.
	 * @param newState
	 */
	protected void changeState(DeviceState newState) {
		mState = newState;
		sendUIMessage(UIEvent.StateChange, newState.ordinal());
	}

	/**
	 * Un-pauses data transmission and changes state to 'Transmitting'.
	 */
	@Override
	public void startEvents() {
		if (debug) Log.v(TAG, "DummySourceThread.start()");
		paused = false;
		changeState(DeviceState.Transmitting);
	}
	
	/**
	 * Pauses data transmission. State drops back to 'Ready'.
	 */
	@Override
	public void stopEvents() {
		if (debug) Log.d(TAG, "DummySourceThread.stopEvents()");
		paused = true;
		changeState(DeviceState.Ready);
	}
	
	@Override
	public void configure(Bundle parameters) {
		// nop
	}

	/**
	 * The primary function of the thread that comprises this dummy EventSource.
	 */
	@Override
	public void run() {
		long lastEvent = 0;
		long prevEvent = 0;
		initBeforeRun();

		Log.i(TAG, "DummySourceThread.run()");
		Log.i(TAG, "DummySourceThread|ThreadPriority="+this.getPriority());
		
		changeState(DeviceState.Ready);
		
		if (transmissionAutoStart) {
			startEvents();
		}

		while(!thread_should_die) {

			if (paused) {
				try {
					Thread.sleep(PausedSleepTime); /* Can entire thread not simply be paused instead? */
				} catch (InterruptedException e){}
			} else {
				prevEvent = lastEvent;
				lastEvent = awaitNextEvent(lastEvent);
				long timeSincePrev = Math.min(prevEvent-lastEvent, 10000);

				doRun(lastEvent, timeSincePrev);
			}
			
		} /* while() */
		Log.i(TAG, "SourceThread: thread dieing.");
	} /* run() */
	
	/**
	 * For
	    long lastBPMDebugReport = 0;
		long transientUStart = 0;
		float transientDesired = 0;
	 */
	protected abstract void initBeforeRun();
	
	protected abstract void doRun(long lastEvent, long timeSincePrev);
	
	
	/**
	 * awaitNextEvent() must simply block until the next event
	 * should occur. In the case of
	 * regularly sampled data (like ECG), this should
	 * just block for a fixed period of time (the sample period)
	 * as defined from the lastEvent.
	 * In the case of event-driven data generator (e.g. BPM)
	 * this should block until the next event (like pulse) should
	 * occur.
	 * 
	 * @param lastEvent - the time at which the previous event occurred
	 * @return the time of the current event (i.e. now). this is critical.
	 */
	protected abstract long awaitNextEvent(long lastEvent);
	
}
