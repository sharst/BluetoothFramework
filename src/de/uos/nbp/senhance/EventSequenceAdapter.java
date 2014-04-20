/**
 * 
 */
package de.uos.nbp.senhance;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


/**
 * @author rmuil
 *
 */
public class EventSequenceAdapter extends ArrayAdapter<EventTag> {
	@SuppressWarnings("unused")
	private static final String TAG = "Senhance";
	
	private int mViewResourceId;
	private boolean itemsDisabled; /* all events in the list are to be displayed disabled */
	
	private static final SimpleDateFormat dateFmt = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss", Locale.US);

	public EventSequenceAdapter(Context context, int viewResourceId,
			List<EventTag> objects) {
		super(context, viewResourceId, objects);
		mViewResourceId = viewResourceId;
		itemsDisabled=false;
	}

	/* 
	 * (non-Javadoc)
	 * @see android.widget.ArrayAdapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		EventTag event = super.getItem(position);
		CharSequence n = event.getName();
		CharSequence d = event.getDescription();
		String datestr = dateFmt.format(event.getTime());
		
		Date now = new Date();
		
		if (dateFmt.format(now).regionMatches(0, datestr, 0, 10)) {
			datestr = datestr.substring(11, datestr.length());
			Log.v("heartFelt", "Date on same day, substr=["+datestr+"]");
		}
		//Log.d(TAG, position+"name="+n+", desc="+d+", time="+t);
		
		View v = convertView;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(mViewResourceId, null);
        }
		
		TextView name = (TextView) v.findViewById(R.id.eventItemName);
		TextView desc = (TextView) v.findViewById(R.id.eventItemDesc);
		TextView time = (TextView) v.findViewById(R.id.eventItemTime);
		
		name.setText(n);
		desc.setText(d);
		time.setText(datestr);
		
		if (itemsDisabled) {
			//Log.d(TAG, "im off, they're "+(name.isEnabled()?"on":"off"));
			name.setEnabled(false);
			desc.setEnabled(false);
			time.setEnabled(false);
		} else {
			//Log.d(TAG, "im on, they're "+(name.isEnabled()?"on":"off"));
			name.setEnabled(true);
			desc.setEnabled(true);
			time.setEnabled(true);
		}
		
		return v;
	}

	public void setItemsDisabled(boolean itemsDisabled) {
		this.itemsDisabled = itemsDisabled;
	}
}
