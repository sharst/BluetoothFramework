package de.uos.nbp.senhance.datasource;

/**
 *
 * EventSink is the counterpart to EventSource and is a consumer of the
 * events. These data events are at the moment assumed to be
 * heart-related events but this could conceivably be generalized.
 * 
 * The implementing class of an EventSink must provide a clock mapping
 * to relate event reception to event 'playback' and deal with variable
 * latency in the event production process.
 *
 * Implementing classes should be thread safe. The trigger, 
 * updateClockMapping and resetClockMapping functions
 * may be called from threads other than the constructing thread.
 *
 * @author rmuil
 */
public interface DataSink {
	static final int DefaultThreadPriority = Thread.MAX_PRIORITY;

	void init();
	void trigger(long sampleTime, boolean isPulse, int rawData);
	int updateClockMapping (long receptionTime, long sampleTime);
	void resetClockMapping ();
	void play();
	void pause();
}
