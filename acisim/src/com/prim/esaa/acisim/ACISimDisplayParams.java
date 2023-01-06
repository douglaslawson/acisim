package com.prim.esaa.acisim;

import com.prim.esaa.gui.SimDisplayParams;

public class ACISimDisplayParams extends SimDisplayParams {

	  public ACISimDisplayParams(double d, boolean b, int evolving, double e, boolean c, boolean d2) {
		  super(d,b,evolving,e,c,d2);
	}

	public int getPassengerMaximumCount() {
		  return maxpax;
	  }
	
	  public int getBoardingPattern() {
		  return bp;
	  }

	public void setBoardingPattern(int b) {
		bp = b;		
	}

	public void setPassengerMaximumCount(int mp) {
		maxpax = mp;	
	}
}
