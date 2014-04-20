/**
 * 
 */
package de.uos.nbp.senhance;

import java.util.Date;

/**
 * @author rmuil
 *
 */
public class EventTag {

	private CharSequence	mName;
	private CharSequence	mDescription;
	private Date			mTime;
	
	public EventTag(CharSequence name, CharSequence description, Date time) {
		setName(name);
		setDescription(description);
		setTime(time);
	}

	public void setName (CharSequence name) {
		this.mName = name;
	}

	public CharSequence getName () {
		return mName;
	}
	
	public void setDescription(CharSequence description) {
		this.mDescription = description;
	}

	public CharSequence getDescription() {
		return mDescription;
	}

	public void setTime(Date time) {
		this.mTime = time;
	}

	public Date getTime() {
		return mTime;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("EventTag[");
		builder.append(mTime);
		builder.append(":");
		builder.append(mName);
		builder.append("|");
		builder.append(mDescription);
		builder.append("]");
		return builder.toString();
	}
}
