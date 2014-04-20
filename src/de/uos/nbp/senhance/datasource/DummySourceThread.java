package de.uos.nbp.senhance.datasource;

import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import de.uos.nbp.Utils;
import de.uos.nbp.senhance.DataLogger;

import android.os.Bundle;
import android.os.Handler;
//import android.os.SystemClock;
import android.util.Log;

/**
 * 
 * This class is intended as a base for dummy DataSources
 * where 'dummy' indicates that there is no underlying
 * event generator - data is generated in a simple form.
 * 
 * The rate is the super-position of a baseline, noise and transients.
 * The baseline is simply the base rate of the DataSource and is analogous
 * to the average heart rate of a person. Noise is a random walk around the
 * baseline. A transient can be triggered by the calling class, and
 * is intended to mimic the cardiac response (such as the orienting response)
 * to events such as visual stimuli -
 * for a certain period, the rate will respond in a certain predefined way:
 *  for example, dip and then rise and then return to baseline.
 * 
 * The randomization for the noise is a very simple random walk
 * in which the RR interval either goes up or down
 * (*changing* direction with a certain chance)
 * by a random percentage every half-a-second.
 * 
 * @author rmuil@UoS.de
 *
 */
public abstract class DummySourceThread extends Thread implements DataSource {
	static final String TAG = "Senhance";
	static final boolean D = true;

	private final static long MaximumThreadCloseTime = 200;

	private final static long RandomInterval = 500;
	//private final static float DirChangeChance = (float)0.1;
	private final static float MaxPercChange = (float)0.5;
	private final static float MinPercChange = (float)0.2;
	private final static boolean DefIsRandomized = true;
	private final static long PausedSleepTime = 200;

	private static final float MaxNoiseBPM = 4;
	
	private static final float TransientOnsetInc = 10; /* BPM per second */
	private static final float DefTransientUBPM = -5;
	private static final float DefTransientVBPM = 10;
	private static final long DefTransientULength = 800; /*msecs - should perhaps be heart beats? */
	private static final int DebugBPMReportInterval = 500; /* msecs */

	public abstract String getDeviceIDString();

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
	
	/**
	 * generateData() is where the actual data is generated.
	 * 
	 * At the moment, almost all functionality of data
	 * generation and persistence should be implemented
	 * here. So, this function should:
	 * 1. Generate whatever data needs to be generated
	 *   (e.g. ECG voltages and R-wave)
	 * 2. Send this data on to the EventSinks
	 * 3. Persist the data with the EventLogger
	 * 
	 * This function should use the 'baseline' to determine
	 * the statistical properties of the data to take advantage
	 * of the randomization implemented here.
	 * 
	 * It is possible that in the future the persistence of
	 * the data, and the forwarding of it to the EventSinks
	 * will be factored out into this superclass.
	 * 
	 * @return any salient event in the data, such as an RWave
	 */
	protected abstract int generateData();

	/**
	 * This should ensure that the event logger is
	 * configured correctly and data is ready to be
	 * logged.
	 * @return success
	 */
	public abstract boolean prepareStorage();
	
	
	protected DataLogger mDataLogger = null;
	protected DeviceState mState = DeviceState.Disconnected;
	protected int mID = -1;

	private boolean transmissionAutoStart = false;
	
	private CopyOnWriteArrayList<DataSink> mEventSinks = null;
	private int numEventSinks = 0;
	private Handler mHandler = null;
	private boolean paused = true;
	private boolean randomGoingUp = true;
	private Random randomGen;
	private long lastRandom = 0;
	private boolean randomized = DefIsRandomized;
	private float noise = 0; /* noise (the random walk) in BPM */
	private float transientCurrent = 0; /* the current level of a currently occurring transient response in BPM */
	private long transientULength = DefTransientULength;

	private float transientUBPM = DefTransientUBPM;
	private float transientVBPM = DefTransientVBPM;

	/**
	 * Transient Responses are modeled as sequences of phases, named by this
	 * enumeration.
	 *
	 * @author rmuil
	 * April 16, 2012
	 */
	enum TransientPhase {
		inactive,
		pre, /* before U - response not started, waiting for trigger */
		U, /* D1 in orienting response */
		V, /* A1 in orienting response */
		W, /* D2 in orienting response NOT CURRENTLY IMPLEMENTED */
		post /* after response - returning to baseline */
	}
	/**
	 * transientPhase indicates whether a transient heart response is 
	 * currently occurring and in what phase it is.
	 */
	TransientPhase transientPhase = TransientPhase.inactive; /* the phase (component) of current transient response */
	private boolean thread_should_die = false;
	
	public DummySourceThread() {
		Log.d(TAG, "DummySourceThread()");
		
		mEventSinks = new CopyOnWriteArrayList<DataSink>();
		randomGen = new Random();
		
		try {
			this.setPriority(DefaultThreadPriority);
		} catch (SecurityException e) {
			Log.w(TAG, "Got SecurityException trying to set device thread priority: "+e);
		} catch (IllegalArgumentException e1) {
			Log.e(TAG, "oops, gave the wrong priority: "+e1);
		}

		Log.i(TAG, "DummySourceThread|ThreadPriority="+this.getPriority());
	}
	
	
	@Override
	public void attachLogger(DataLogger eventLogger) {
		if (D) Log.d(TAG, "DummySourceThread.attachLogger()");
		mDataLogger = eventLogger;
		prepareStorage();
	}


	
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
	
	/**
	 * Enable noise.
	 * @param isRandomized
	 */
	private void setRandomized(boolean isRandomized) {
		//synchronized(randLock) {
			randomized = isRandomized;
			Log.i(TAG, "DummySource randomization " + (randomized ? "enabled" : "disabled"));
		//}
	}
	
	@Override
	public void configure(Bundle parameters) {
		if (parameters.containsKey("isRandomized")) {
			setRandomized(parameters.getBoolean("isRandomized"));
		}
		if (parameters.containsKey("transientULength")) {
			transientULength = parameters.getLong("transientULength");
		}
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
	 * no auto-start.
	 */
	@Override
	public void connect(String address) {
		changeState(DeviceState.Connecting);
		prepareStorage();
		if (!super.isAlive())
			super.start(); /* call thread start() */
	}
	
	/**
	 * default is to not auto-start.
	 */
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
		if (D) Log.v(TAG, "DummySourceThread.start()");
		paused = false;
		changeState(DeviceState.Transmitting);
	}
	
	/**
	 * Pauses data transmission. State drops back to 'Ready'.
	 */
	@Override
	public void stopEvents() {
		if (D) Log.d(TAG, "DummySourceThread.stopEvents()");
		paused = true;
		changeState(DeviceState.Ready);
	}
	
	/**
	 * Sets the current value of the data generation device, necessary to
	 * be accessible at this level of the class hierarchy so that the generic
	 * randomization algorithm can call it.
	 */
	abstract protected void setCurrent(float newCurrent);
	
	/**
	 * Begin a transient response with amplitudes given in BPM relative to
	 * baseline.
	 * 
	 * @param u_amplitude - in BPM (as offset from baseline)
	 * @param v_amplitude - in BPM (as offset from baseline)
	 */
	public void startTransient(float u_amplitude, float v_amplitude) {
		transientUBPM = u_amplitude;
		transientVBPM = v_amplitude;
		transientPhase = TransientPhase.pre;
		if (D) Log.i(TAG, "Transient enter pre (START)");
	}
	
	public void endTransient() {
		if (transientPhase == TransientPhase.inactive)
			return;
		transientPhase = TransientPhase.post;
		if (D) Log.i(TAG, "Transient enter post");
	}
	
	/**
	 * The primary function of the thread that comprises this dummy EventSource.
	 */
	@Override
	public void run() {
		long lastEvent = 0;
		long prevEvent = 0;
		long lastBPMDebugReport = 0;
		long transientUStart = 0;
		float transientDesired = 0;

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

				@SuppressWarnings("unused")
				int rwave = generateData();

				switch(transientPhase) {
				case pre:
					transientDesired = 0;

					transientPhase = TransientPhase.U;
					transientDesired = transientUBPM;
					if (D) Log.i(TAG, "Transient enter U ("+transientUBPM+"bpm)");
					transientUStart = lastEvent;
					
					break;
				case U:
					if ((lastEvent - transientUStart) > transientULength) {
						transientPhase = TransientPhase.V;
						transientDesired = transientVBPM;
						if (D) Log.i(TAG, "Transient enter V ("+transientVBPM+"bpm)");
					}
					break;
				case V:
					break;
				case post:
					if (Math.abs(transientCurrent) <= TransientOnsetInc) {
						transientCurrent = 0;
						transientPhase = TransientPhase.inactive;
						if (D) Log.i(TAG, "Transient enter inactive (END)");
					}
				case inactive:
				default:
					transientDesired = 0;
					break;
				
				}
				transientCurrent = Utils.smoothSet(
						transientCurrent,
						transientDesired,
						TransientOnsetInc,
						timeSincePrev);
					
				if (randomized) {
					if ((lastEvent-lastRandom)>RandomInterval) {
						
						float dirChangeChance = 0;
						if (randomGoingUp && noise > 0) {
							dirChangeChance = noise/(MaxNoiseBPM*2);
						} else if (!randomGoingUp && noise < 0) {
							dirChangeChance = -noise/(MaxNoiseBPM*2);
						}
						
						if (randomGen.nextFloat() < dirChangeChance) /* chance of changing direction */
							randomGoingUp = !randomGoingUp;

						float inc =
							(randomGen.nextFloat() *
									(MaxPercChange - MinPercChange)) + MinPercChange;

						if (!randomGoingUp)
							inc = -inc;
						
						noise += inc;
						
						if (noise > MaxNoiseBPM)
							noise = MaxNoiseBPM;
						if (noise < -MaxNoiseBPM)
							noise = -MaxNoiseBPM;

						lastRandom = lastEvent;
					}
				} else {
					noise = Utils.smoothSet(
							noise,
							0,
							TransientOnsetInc,
							timeSincePrev);
					lastRandom = lastEvent;
				}
				setCurrent(getBaseline() + noise + transientCurrent);
				if ((lastEvent-lastBPMDebugReport)>DebugBPMReportInterval) {
					if (D) Log.d(TAG, "baseline="+getBaseline()+"; " + (randomized ? "NOISE=" : "noise=")+noise+"; transient="+transientCurrent);
					lastBPMDebugReport = lastEvent;
				}
			}
		} /* while() */
		Log.i(TAG, "DummySourceThread: thread dieing.");
	} /* run() */

}
