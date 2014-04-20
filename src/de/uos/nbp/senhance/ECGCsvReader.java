package de.uos.nbp.senhance;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a csv file containing ecg data. Assumes at least two columns, where the first column contains
 * the time and the sceond ecgChII. Everything starting with # is ignored.
 * 
 * @author aswolinskiy
 *
 */
public class ECGCsvReader {
	
	private int[] times;
	private int[] ecgChII;
	
	private int maxEntryNr = Integer.MAX_VALUE;
	
	public void read(BufferedReader br){
		try {
			List<String> lines = new ArrayList<String>();
			
			String line = null;
			int lineNumber = 0;
 
			while( (line = br.readLine()) != null && lineNumber < maxEntryNr){
				if (line.startsWith("#")){
					continue;
				}
				lines.add(line);
				lineNumber++;
			}
			
			times = new int[lines.size()];
			ecgChII = new int[lines.size()];
			
			for (int i=0; i<lines.size();i++){
				line = lines.get(i);
				String[] parts = line.split(",");
				//TODO: asserts
				times[i] = Integer.parseInt(parts[0]);
				ecgChII[i] = Integer.parseInt(parts[1]);
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	public int[] getTimes() {
		return times;
	}



	public int[] getEcgChII() {
		return ecgChII;
	}



	public void setMaxEntryNr(int maxEntryNr) {
		this.maxEntryNr = maxEntryNr;
	}

}
