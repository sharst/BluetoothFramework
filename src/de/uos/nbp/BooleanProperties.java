/**
 * 
 */
package de.uos.nbp;

import java.util.HashMap;

/**
 * This is very simple. Just used to store simple boolean
 * values against some form of key.
 * 
 * Used because a raw hash map requires testing for key existence
 * and then testing for boolean value, which clutters code.
 * 
 * A BooleanProperty can have 3 states: true, false or undefined.
 * 
 * TODO: Is this the best way to do this, actually?
 * 
 * @author rmuil
 *
 */
public class BooleanProperties<T> extends HashMap<T, Boolean> {

	private static final long serialVersionUID = 2858090367270077089L;
	
	/**
	 * Returns true if and only if the provided key is present AND true.
	 * @param key
	 * @return true iff key is present AND true
	 */
	public boolean isTrue (T key) {
		boolean retVal = false;
		
		if (this.containsKey(key) &&
			this.get(key)) {
			retVal = true;
		}
		return retVal;
	}
	
	/**
	 * Returns true if and only if the provided key is present AND false.
	 * @param key
	 * @return true iff key is present AND false
	 */
	public boolean isFalse (T key) {
		boolean retVal = false;
		
		if (this.containsKey(key) &&
			!this.get(key)) {
			retVal = true;
		}
		return retVal;
	}
	
	/**
	 * Returns true if and only the provided key has not been set (is not present).
	 * @param key
	 * @return true iff key is absent
	 */
	public boolean isUndefined (T key) {
		return !this.containsKey(key);
	}
}
