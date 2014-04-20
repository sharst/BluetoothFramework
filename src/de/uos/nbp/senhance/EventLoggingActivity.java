/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.uos.nbp.senhance;


import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import kankan.wheel.widget.OnWheelChangedListener;
import kankan.wheel.widget.OnWheelClickedListener;
import kankan.wheel.widget.OnWheelScrollListener;
import kankan.wheel.widget.WheelView;
import kankan.wheel.widget.adapters.ArrayWheelAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * This Activity appears as a dialog, enabling events to be logged.
 */
public class EventLoggingActivity extends Activity {
    // Debugging
	private static final String TAG = "heartFelt";
    @SuppressWarnings("unused")
	private static final boolean D = true;

    // Return Intent extra
    public static String EVENT_NAME = "event_name";
    public static String EVENT_DESC = "event_description";
    public static String EVENT_TIME = "event_time";
    
    private static final int UpcomingEventsWheelVisibleItems = 4;
    private static final int UpcomingEventsWheelTextSize = 14;
    
	/* 
	 * TODO: these should be in some configuration file, not hard-coded.
	 */
	final String[] event_sequences = {
			"General Activities",
			"Physical: Lying-to-Standing",
			"Physical: Bending-Double",
			"Experiment: Interospective Awareness"
	};
	final int[] event_sequence_arrays = {
			R.array.event_sequence_standard,
			R.array.event_sequence_lying_to_standing,
			R.array.event_sequence_bending_double,
			R.array.event_sequence_experiment_introspective_awareness
	};
	final boolean[] event_sequence_editable = {
			true,
			false,
			false,
			false
	};
	
    // Member fields
    ArrayWheelAdapter<CharSequence> upcomingEventsAdapter;
    WheelView upcomingEventsWheel;
    boolean upcomingEventsScrolling = false;
	protected boolean sequenceIsEditable = true;
    
    TextView upcomingEventsTitle;
    TextView nextEventTitle;
    TextView pastEventsTitle;
    TextView nextEventInstructions;
    AutoCompleteTextView nextEventName;
    AutoCompleteTextView nextEventDesc;
     
    Button logBtn;
    Button logCancelBtn;
    Button setSeqBtn;
    CheckBox autoAdvanceCheckBox;
    
    DataLogger mEventLogger;
    
    ListView pastEventsList;
    EventSequenceAdapter	pastEventsAdapter;
	
	Date newEventStart = null; /* this is also used as a state variable */
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Resources res = getResources();

        // Setup the window
        setContentView(R.layout.event_tagging);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
   
        ArrayList<EventTag> eventSequence = new ArrayList<EventTag>();
        
        pastEventsAdapter = new EventSequenceAdapter(this, R.layout.event_item, eventSequence);
        if (pastEventsAdapter == null) {
        	Log.e(TAG, "Failed to construct EventSequenceAdapter...");
        }
        
        pastEventsList = (ListView) findViewById(R.id.pastEventsList);
        if (pastEventsList == null) {
        	Log.e(TAG, "Failed to find pastEventsList");
        }
    
        pastEventsList.setAdapter(pastEventsAdapter);
        
        pastEventsList.setAddStatesFromChildren(true);
        
        nextEventInstructions = (TextView) findViewById(R.id.nextEventInstructions);
        nextEventTitle = (TextView) findViewById(R.id.nextEventTitle);
        pastEventsTitle = (TextView) findViewById(R.id.pastEventsTitle);
        upcomingEventsTitle = (TextView) findViewById(R.id.upcomingEventsTitle);
        autoAdvanceCheckBox = (CheckBox) findViewById(R.id.autoAdvanceCheckBox);
                
        upcomingEventsAdapter = new ArrayWheelAdapter<CharSequence>(this, res.getStringArray(R.array.event_sequence_standard));
        upcomingEventsAdapter.setTextSize(UpcomingEventsWheelTextSize);
        
        nextEventName = (AutoCompleteTextView) findViewById(R.id.nextEventName);
        nextEventDesc = (AutoCompleteTextView) findViewById(R.id.nextEventDesc);
        
        upcomingEventsWheel = (WheelView) findViewById(R.id.upcomingEventsWheelView);
        upcomingEventsWheel.setVisibleItems(UpcomingEventsWheelVisibleItems);
        upcomingEventsWheel.setViewAdapter(upcomingEventsAdapter);
        upcomingEventsWheel.setCyclic(true);
        //upcomingEventsWheel.setBackgroundColor(Color.WHITE);
        //((View)upcomingEventsWheel.getParent()).setBackgroundColor(Color.WHITE);
        
        upcomingEventsWheel.addScrollingListener( new OnWheelScrollListener() {
            public void onScrollingStarted(WheelView wheel) {
                upcomingEventsScrolling = true;
                Log.d(TAG,"scrolling started");
            }
            public void onScrollingFinished(WheelView wheel) {
            	upcomingEventsScrolling = false;
            	Log.d(TAG,"scrolling finished");
            	
            }
        });
     
        upcomingEventsWheel.addChangingListener(upcomingEventsChangedListener);
        upcomingEventsWheel.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				//NB: this doesn't work with current android-wheel code...
				Log.d(TAG, "clicked!!!!!!!!");
			}
		});
        upcomingEventsWheel.addClickingListener(new OnWheelClickedListener() {
			
			@Override
			public void onItemClicked(WheelView wheel, int itemIndex) {
				/*
				 * NB: bug in the android-wheel code such that clicking
				 * on the center item in the wheel doesn't trigger this
				 * unfortunately.
				 */
				CharSequence text = getUpcomingEventText();
	            nextEventName.setText(text);
			}
		});
        
        setSeqBtn = (Button) findViewById(R.id.BtnSetEventSequence);
        setSeqBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				doSelectNewEventSequence();
			}
		});
        autoAdvanceCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				upcomingEventsTitle.setText(isChecked?R.string.upcoming_events_title:R.string.possible_events_title);	
			}
		});
        upcomingEventsTitle.setText(autoAdvanceCheckBox.isChecked()?R.string.upcoming_events_title:R.string.possible_events_title);
        logBtn = (Button) findViewById(R.id.buttonLogEvent);
        logCancelBtn = (Button) findViewById(R.id.buttonCancelLogEvent);
        logCancelBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finishEventLog(false);
			}
		});
        logBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (newEventStart == null) {
					prepareForEventLog();
				} else {
					finishEventLog(true);				
				}
			}
		});
        
    	mEventLogger = ((LoggerApplication)getApplication()).getLogger();
    }
    
    /* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		Log.v(TAG, "EventLoggingActivity.onResume()");
	
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		HashMap<String,String> sn = new HashMap<String,String>(1,(float) 1.0);
		sn.put("dbg", "debug messages");
		mEventLogger.prepare(sn);
	}

	private void prepareForEventLog () {
		newEventStart = new Date();
		
		logBtn.setText(R.string.log_event_button2);
		logBtn.setSelected(true);
		
		nextEventInstructions.setText(R.string.log_event_instructions_2);
		nextEventInstructions.setEnabled(true);
		nextEventTitle.setEnabled(true);
		logCancelBtn.setVisibility(View.VISIBLE);
		nextEventName.setVisibility(View.VISIBLE);
		nextEventDesc.setVisibility(View.VISIBLE);
		
		if (sequenceIsEditable) {
			nextEventName.setEnabled(true);
		}
		
		pastEventsTitle.setEnabled(false);
		pastEventsAdapter.setItemsDisabled(true);
		pastEventsList.invalidateViews();

		upcomingEventsTitle.setText(R.string.possible_events_title);
		setSeqBtn.setEnabled(false);
		autoAdvanceCheckBox.setEnabled(false);
    }
    
    private void finishEventLog (boolean writeLog) {
    	
		if (writeLog) {
			String n = nextEventName.getText().toString();
			String d = nextEventDesc.getText().toString();
			
			if (n.length() <= 0) {
				Toast.makeText(this, R.string.event_tag_name_empty_error,
						Toast.LENGTH_SHORT).show();
				return;
			}
			EventTag event = new EventTag(n, d, newEventStart);
			Log.d(TAG, "New "+event);
			pastEventsAdapter.add(event);
			if (autoAdvanceCheckBox.isChecked() & 
					n.equalsIgnoreCase(getUpcomingEventText().toString())) {
				advanceEventSequence();
			}
			n = n.replace('|', '_');
			n = n.replace('=', '~');
			StringBuilder sb = new StringBuilder("UserEvent=");
			sb.append(n);
			if (d.length()>0) {
				d = d.replace('=', '~');
				d = d.replace('|', '_');
				sb.append("|");
				sb.append(d);
			}
			mEventLogger.writeDate(sb.toString(), newEventStart);
		}
		
		newEventStart = null;
		
		logBtn.setText(R.string.log_event_button);
		logBtn.setSelected(false);
		
		upcomingEventsTitle.setText(autoAdvanceCheckBox.isChecked()?R.string.upcoming_events_title:R.string.possible_events_title);
		nextEventInstructions.setText(R.string.log_event_instructions_1);
		nextEventInstructions.setEnabled(false);
		nextEventTitle.setEnabled(false);
		logCancelBtn.setVisibility(View.GONE);
		nextEventName.setVisibility(View.GONE);
		nextEventDesc.setVisibility(View.GONE);
		
		nextEventName.setEnabled(false);
		
		pastEventsTitle.setEnabled(true);
		pastEventsAdapter.setItemsDisabled(false);
		pastEventsList.invalidateViews();
		setSeqBtn.setEnabled(true);
		autoAdvanceCheckBox.setEnabled(true);
		nextEventDesc.setText("");
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(nextEventName.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }
    
    private void advanceEventSequence() {
    	nextEventName.setText(getUpcomingEventText());
    	upcomingEventsWheel.scroll(1, 200);
    }
    
    private CharSequence getUpcomingEventText(int item) {
    	return upcomingEventsAdapter.getItemText(item);
    }
    private CharSequence getUpcomingEventText() {
    	return getUpcomingEventText(upcomingEventsWheel.getCurrentItem());
    }
    @SuppressWarnings("unused")
	private CharSequence getPreviousUpcomingEventText() {
    	int item = upcomingEventsWheel.getCurrentItem();
    	if (item == 0)
    		item = upcomingEventsAdapter.getItemsCount() - 1;
    	else
    		item--;
    	
    	return (item > 0)?getUpcomingEventText(item):null;
    }
    
    // Wheel changed listener
    private OnWheelChangedListener upcomingEventsChangedListener = new OnWheelChangedListener() {
        public void onChanged(WheelView wheel, int oldValue, int newValue) {
        	Log.d(TAG, "wheel changed");
        	if (!upcomingEventsScrolling) {
        		Log.d(TAG, " -> outside of scrolling");
        	}
        	if (newEventStart != null) {
    			Log.d(TAG, " -> while adding event");
        	}
    		CharSequence text = upcomingEventsAdapter.getItemText(newValue);
            nextEventName.setText(text);	
        }
    };
    
    public void doSelectNewEventSequence() {
    
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Select an event sequence");
		builder.setItems(event_sequences, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

				sequenceIsEditable  = event_sequence_editable[which];
				CharSequence [] newSeq = getResources().getTextArray(event_sequence_arrays[which]);

				upcomingEventsAdapter = new ArrayWheelAdapter<CharSequence>(getBaseContext(), newSeq);
				upcomingEventsAdapter.setTextSize(UpcomingEventsWheelTextSize);
				upcomingEventsWheel.setViewAdapter(upcomingEventsAdapter);
				nextEventName.setText(getUpcomingEventText());
			}
		});
		AlertDialog selector = builder.create();
		selector.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
