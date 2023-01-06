package com.prim.esaa.acisim;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;
import java.awt.Color;

import com.prim.esaa.*;
import com.prim.esaa.aci.ACAisle;
import com.prim.esaa.aci.ACBin;
import com.prim.esaa.aci.ACEntryWay;
import com.prim.esaa.aci.ACGalleyWay;
import com.prim.esaa.aci.ACISpatialModeler;
import com.prim.esaa.aci.ACRowWay;
import com.prim.esaa.gui.SimDisplay;
import com.prim.esaa.gui.SimDisplayParams;
import com.prim.esaa.sta.STASimParams;
import com.prim.esaa.travel.Passenger;

/**
 * ACGridSim.java
 *
 *
 * Created: Sat Dec  6 14:53:17 2003
 *
 * @author <a href="mailto:dalawson@prim-usa.com">Douglas Lawson</a>
 * @version 1.0.0
 */


public class ACGridSim extends Simulator {
    static int iprescor = 0;
    static int ineededrow = 1;
    static int ineededseat = 2;
    static int ixloc = 3;
    static int istat = 4;
    static int idir = 5;
    static int ifrow = 6;
    static int iwait = 7;
    static int istowtime = 8;
    static int ibagsize = 9;
    static int iacrow = 10;
    static int iacseat = 11;
    static int ipax = 0;

    public static final int NUM_PROPERTIES = iacseat+1;
    public static final int STATUS_MOVINGAHEAD = 0;
    public static final int STATUS_COMINGOUT = 1;
    public static final int STATUS_STEPPINGASIDE = 2;
    public static final int STATUS_RETURNING = 3;
    public static final int STATUS_STOWING = 4;
    public static final int STATUS_WAITING = -1;
    public static final int STATUS_NOT_WAITING = -2;

    public static final int MAX_PAX = 137;
    public static final int MAX_ROWS = 23;
    public static final int STOW_SECONDS = 5;

     static Hashtable htmovedoccupants = null;
     static ArrayList seatedpax = null;
     static ArrayList blockers = null; // contains int[2] that have the setters inum, removers inum, and row blocked
 
    static int[][]pax = null;
    static double[] paxpercentingroup = {0.7872, 0.1524, 0.0286, 0.0187, 0.0055}; // single, double, triple, quadruple, quintuple
  static String[] seatlabels = {"C","B","A","D","E","F"};

    static int[] seatxpos = {0,0,2,2,1,1}; // elements 0,1,2,3,4,5
    static int[] corspeed = {12, 10, 6};
    static int[] corcellsize = {15, 11, 17};
    static int[] corlength = {46, 880, 51};
    static int aislecellsperrow = 3;
    static int[] overheadbin;
    static int entryindex = 0;
    static int aisleindex = 1;
    static int rowindex = 2;
    static int seatindex = 3;
    static int totalneedtocomeout = 0;
    static int totalhavestoodaside = 0;

  static Vector vtsobjs =  null;
  static ArrayList alpax = new ArrayList();
  static ACISpatialModeler acispatialmodeler = null;
  static Passenger passenger = null;
  static Color color = new Color(0, 0, 126);
  static ACAisle acaisle = null;
  static ACEntryWay acentryway = null;
  static ACGalleyWay acgalleyway = null;
  static ACRowWay acrowway = null;
  static ACBin  acbin = null;
  static ArrayList alLeftRows = new ArrayList(MAX_ROWS);
  static ArrayList alRightRows = new ArrayList(MAX_ROWS);
  static SimDisplay simdisp = null;
  static int itpax = 0;                               // thru passenger count
  static int boardingpax = MAX_PAX;                   // boarding passeneger count
  static int iseatingflag = 0;
  static Random random = new Random();

  public ACGridSim (){
    
  }
  public static void main(String[] args) {
    iseatingflag = Integer.parseInt(args[0]);
    if(args.length > 2) itpax = Integer.parseInt(args[2]);
    initializePassengers(iseatingflag, itpax, Integer.parseInt(args[1]));
    // this.simulate(iseatingflag,itpax,boardingpax); 
}

  /*
   * this method not only initializes the simulation but also runs it. In the agent-based models
   * the SimClock is used to "runs" the simulation, but here the code is just executed
   * @param acidp parameters including passenger count
   * @param disp SimDisplay to display aircraft interior
   */
  public void initialize(SimDisplayParams acidp, SimDisplay disp ){
    Passenger passenger = null;
    simdisp = disp;
    int iseatingflag  = 0;
    int inboundpax = (int)((double)MAX_PAX*0.70);
    int boardingpax = 0;
    double percentthru = 0.0;
    int itpax = (int)((double)inboundpax*percentthru);
    //
    // initialize
    //
    try {
      acispatialmodeler = (ACISpatialModeler)Class.forName(acidp.getSpatialModel()).newInstance();
    } catch(ClassNotFoundException cnfe) {cnfe.printStackTrace(); return;}
    catch (IllegalAccessException iae) { iae.printStackTrace(); return;}
    catch (InstantiationException ie) { ie.printStackTrace(); return;}
    
    acispatialmodeler.buildModel();
    //
    // initialize stationary objects
    //
    acentryway = acispatialmodeler.getForwardEntryWay();
    acaisle = acispatialmodeler.getAisle();
    vtsobjs = acispatialmodeler.getModel();
    Corridor tmpcor = null;
    for (int i = 0; i < vtsobjs.size(); i++) {
      tmpcor = (Corridor)vtsobjs.elementAt(i);
      tmpcor.setStepResolution(true);
      tmpcor.addObserver(simdisp);
      simdisp.update(tmpcor,tmpcor);
    }
    createRowArrays(vtsobjs);
    //
    // give each person a seat assignment according to preference
    //
    //
    boardingpax = (int)(MAX_PAX*((double)acidp.getPassengerCount())/100.0);
    itpax = ((itpax+boardingpax) > MAX_PAX ? MAX_PAX-boardingpax : itpax);
    Vector vpassengers = initializePassengers(iseatingflag, itpax, (itpax+boardingpax));
    this.simulate(iseatingflag, itpax, boardingpax, vpassengers); 
  }
  
  public static Vector initializePassengers(int iseatingflag, int itpax, int totalpax){
    Vector vpassengers = null;
    if(totalpax < 1) totalpax = 1;
    boardingpax = totalpax - itpax;
    if(totalpax > MAX_PAX) {
      totalpax = MAX_PAX;
      boardingpax = MAX_PAX - itpax;
    }
    if(iseatingflag == 0) {
      //      System.out.println("Open Seating Preferred Short Haul");
      vpassengers = acispatialmodeler.generateOpenSeatingPassengers(boardingpax,1);
      pax = openSeatingPreferredShortHaul(totalpax);
    } else if(iseatingflag == 1){
      //      System.out.println("Open Seating Preferred Long Haul");
      pax = openSeatingPreferredLongHaul(totalpax);
    } else  if(iseatingflag == 2){
      //      System.out.println("Assigned Seating By Row Preferred Long Haul");
      pax = assignedSeatingByRowPreferredLongHaul(totalpax);
    } else  if(iseatingflag == 3){
      //      System.out.println("Assigned Seating WILMA By Seat Preferred Short Haul");
      vpassengers = acispatialmodeler.generateWilmaPassengers(boardingpax);
      pax = assignedSeatingBySeatPreferredShortHaul(totalpax);
    } else  if(iseatingflag == 4){
      //      System.out.println("Assigned Seating WILMA By Seat Preferred Long Haul");
      pax = assignedSeatingBySeatPreferredLongHaul(totalpax);
    } else if(iseatingflag == 5){
      System.out.println("Open Seating By Zone(WM & A) Preferred Short Haul");
      pax = openSeatingByZonePreferredShortHaul(totalpax);
    } else  if(iseatingflag == 6){
      //      System.out.println("Assigned Seating By Row Preferred Sort Haul");
      pax = assignedSeatingByRowPreferredShortHaul(totalpax);
    } else {
      System.out.println("Open Seating By Zone(WM & A) Preferred Long Haul");
      pax = openSeatingByZonePreferredLongHaul(totalpax);
    }

    if(itpax > 0 )placeThruPassengers(itpax, pax, boardingpax);
    //    ACGridSim acsim = new ACGridSim();
    //      acsim.simulate(iseatingflag,totalpax);
    return vpassengers;
  }

  public void simulate(int iseatingflag, int itpax, int boardingpax, Vector vpassengers) {
    int totalpax = boardingpax + itpax;
    iprescor = 0;
    ineededrow = 1;
    ineededseat = 2;
    ixloc = 3;
    istat = 4;
    idir = 5;
    ifrow = 6;
    iwait = 7;
    ipax = 0;

    aislecellsperrow = 3;
    entryindex = 0;
    aisleindex = 1;
    rowindex = 2;
    seatindex = 3;
    htmovedoccupants = new Hashtable();
    if(seatedpax == null) seatedpax = new ArrayList();
    blockers = new ArrayList(); // contains int[2] that have the setters inum, removers inum, and row blocked
    totalneedtocomeout = 0;
    totalhavestoodaside = 0;
    //
    // for each tick of the clock
    //
    //
    // try to put the next pax into the top of the entry way
    //
    //    int nextpax = itpax+2;
    itpax = -1;
    int nextpax = itpax+1;
    Integer ii = null;
    int steps = 0;
    //    alpax.add(0,vpassengers.elementAt(0));
    //    setPassenger(0, acentryway, alpax, pax);
    //    acentryway.fitInMovingObject((MovingObject)alpax.get(0), false);

    while(nextpax < totalpax || seatedpax.size() < totalpax) {
      boolean found = false;
      int i = itpax+1;
      if(nextpax < totalpax ) {
        while ( i < nextpax && !found) {
          if(pax[i][iprescor] == entryindex && pax[i][ixloc]/corcellsize[entryindex] == 0 ) found = true;
	  i++;
        }
        if(!found) {
	  nextpax++;
	  alpax.add(i,vpassengers.elementAt(i));
	  setPassenger(i,acentryway, alpax, pax); 
	  acentryway.fitInMovingObject((MovingObject)alpax.get(i), false);
        }
      }
      //
      // increment the x of each pax in each corridor
      //
      i = 0;
      while ( i < nextpax ) {
	//
	// if the ith pax is in its row and at its seat position
	// if so add the ith pax to the seated list if necessary
	// any remove any blockers placed in the aisle by this pax
	// otherwise try moving the ith pax
	if(pax[i][iprescor] == rowindex && 
	   pax[i][ixloc]/corcellsize[pax[i][iprescor]] == pax[i][ineededseat] &&
	   (pax[i][istat] != STATUS_COMINGOUT )){
	  pax[i][ixloc] = corcellsize[pax[i][iprescor]]*pax[i][ineededseat] + corcellsize[pax[i][iprescor]]/2;
	  System.out.println("Final Display" + i );
	  passenger = (Passenger)alpax.get(i);
	  passenger.c.cctr.x = pax[i][ixloc];	
	  passenger.c.pctr.x = passenger.c.cctr.x - passenger.c.cdx;
	  passenger.c.pctr.y = passenger.c.cctr.y;
          passenger.c.cctr.y = (pax[i][iacseat] < 3 ? acrowway.getWidth()-(passenger.getLength()/2) :  passenger.getLength()/2);
          updateSimDisplay(vtsobjs);
	  ii = new Integer(i);	  
	  if(!seatedpax.contains(ii)) seatedpax.add(ii);
	  if(!htmovedoccupants.contains(ii)) htmovedoccupants.remove(ii);
	  int j = 0;
	  found = false;
	  while( j < blockers.size() && !found) {
	    int[] blocker = (int[])blockers.get(j);
	    if(blocker[1] == i) {
	      blockers.remove(blocker);
	      found = true;
	      //      System.out.println("Seated Removing blocker " + i + " setter " + blocker[0] + " remover " + blocker[1]+ " row " + blocker[2]);
	    }
	    j++;
	  }
	  if(pax[i][iwait] > STATUS_WAITING) {
	    pax[pax[i][iwait]][iwait] = STATUS_NOT_WAITING;
	    pax[i][iwait] = STATUS_NOT_WAITING;
	  }
	} else {
	  if(pax[i][iwait] != STATUS_WAITING) movePax(i, nextpax, pax);
	}
	writeOutPax(i, pax);
        i++;
      }
      updateSimDisplay(vtsobjs);
      steps++;
    }
    //    for(int j = 0; j < seatedpax.size(); j++) {
    //      int inum = ((Integer)seatedpax.get(j)).intValue();
    //      System.out.println("Seated Passenger " + inum + " steps " + steps);
    //      writeOutPax(inum, pax);
    //    }
    System.out.println(seatedpax.size() + " " + steps);
  }
  
  private static void movePax(int i, int nextpax, int[][] pax) {
    //
    // if the pax is in in the aisle use movePaxInAisle
    // otherwise
    // if the next position in the corridor is empty
    // have the pax step 
    // if the pax location is beyond the corridor length
    // check for a pax in the first position in the next corridor of this pax
    // if there is no one there, move into next corridor
    // if there is step back
    //
	int corindex = pax[i][iprescor];
	if(pax[i][iprescor] == aisleindex) {
	  movePaxInAisle(i, nextpax, pax);
	}else {
	  if(!positionEmpty(i, nextpax, pax, corindex, (pax[i][ixloc]+(corspeed[corindex]*pax[i][idir]))/corcellsize[corindex])) return;
	    pax[i][ixloc] += corspeed[corindex]*pax[i][idir];
	    if(pax[i][ixloc] > corlength[corindex]) {
	      if((corindex+1) < corlength.length && positionEmpty(i, nextpax, pax, corindex+1, 0)) {
	        pax[i][iprescor]++;
	        pax[i][ixloc] = 0;
		changeCorridor(i,nextpax, pax[i][iprescor]-1,pax);
	      }else{
	        pax[i][ixloc] -= corspeed[corindex];
	      }
	    } else if(pax[i][ixloc] < 0) {                                     // if going toward beginning of corridor
	      if(pax[i][istat] == STATUS_COMINGOUT) {                          // if coming out of row to aisle
		//		System.out.println( "movePax status coming out " +  pax[i][ifrow]);
		if( positionEmpty(i, nextpax, pax, aisleindex, pax[i][ifrow])) { // if row position in aisle is empty
	          pax[i][iprescor] = aisleindex;
	          pax[i][ixloc] =  (pax[i][ifrow]*corcellsize[aisleindex]) + (corcellsize[aisleindex]/2);
		  pax[i][idir] = 1;
		  changeCorridor(i, nextpax, corindex, pax);
	        }else{
	          pax[i][ixloc] += corspeed[corindex];
	        }

	      } else {
	        if((corindex-1) >= 0 && positionEmpty(i, nextpax, pax, corindex-1, (corlength[corindex]/corcellsize[corindex]))) {
	          pax[i][iprescor]--;
	          pax[i][ixloc] = 0;
		  changeCorridor(i, nextpax, pax[i][iprescor]+1, pax);
	        }else{
	          pax[i][ixloc] += corspeed[corindex];
	        }
	      }
	    }
	
	}
	((Passenger)alpax.get(i)).c.cctr.x = pax[i][ixloc];	
	((Passenger)alpax.get(i)).c.pctr.x = ((Passenger)alpax.get(i)).c.cctr.x - ((Passenger)alpax.get(i)).c.cdx;
  }

  private static void movePaxInAisle(int i, int nextpax, int[][] pax){
	//
	//
	int corindex = pax[i][iprescor];	
	// if the pax is in the aisle
	// check is x / aisle length equals preferred row
	//
	//	System.out.println("movePaxInAisle " + i + " " + pax[i][ineededrow] + " " + pax[i][ixloc]/corcellsize[aisleindex]);
           if(pax[i][istat] == STATUS_COMINGOUT) {
	     pax[i][istat] = STATUS_STEPPINGASIDE;
	     if(pax[i][iwait] > STATUS_WAITING) {                        // if someone is waiting on this pax
	       pax[pax[i][iwait]][iwait] = STATUS_NOT_WAITING;           // set they both free
	       pax[i][iwait] = STATUS_NOT_WAITING;
	     }
	   }
        if(pax[i][ineededrow] == pax[i][ixloc]/corcellsize[aisleindex]) { // reached the needed row position in aisle
	  
 	    if(pax[i][istat] == STATUS_RETURNING) {
	        if(!positionEmpty(i, nextpax, pax, rowindex, 0)) return;
	        pax[i][iprescor] = rowindex;
	        pax[i][ixloc] = 0;
	        pax[i][idir] = 1;
		changeCorridor(i, nextpax, rowindex, pax);
	    } else if(pax[i][istat] == STATUS_STEPPINGASIDE){                       // are stepping aside
	        int j = -1;
	        boolean found = false;
		int[] blocker = null;
	        while( j < blockers.size()-1 && !found) {
	          j++;
	          blocker = (int[])blockers.get(j);
	          if(blocker[0] == i) {
	            found = true;                     // found pax to be the deepest in row
	          }
	        }
		if(found) {
		    // so the deepest in the row has reached its needed row to step far enough aside
		    // so set all in the paxinrowlist for the asker that are stepping aside to return
                    ArrayList paxinrowlist = (ArrayList)htmovedoccupants.get(new Integer( blocker[1]));
		    for(int n = 0; n < paxinrowlist.size(); n++) {
		      int k = ((Integer)paxinrowlist.get(n)).intValue();
		      //                      System.out.println("stepping aside set to return  paxinrowlist " +  blocker[1] + " " + paxinrowlist.size());
		      //		      writeOutPax(k,pax);
		      if(pax[k][istat] == STATUS_STEPPINGASIDE) 
			{
			  pax[k][istat] = STATUS_RETURNING;
	                  pax[k][ineededrow] = pax[k][ifrow];
	                  pax[k][idir] = -1*pax[k][idir];
			}
		    }
		    // and continue blocking of those behind the original asker
		    // but the block will be removed when the shallowest sits
		    //      System.out.println("stepping aside reached row present blocker " + i + " setter " + blocker[0] + " remover " + blocker[1]+ " row " + blocker[2]);
		    blocker[0] = blocker[1];                                // the original asker is setter
		    blocker[1] = ((Integer)paxinrowlist.get(0)).intValue(); // shallowest in the row is remover
		    // and add a blocker of the returners 
		    // so the asker can get to its seat in the row at which time it will remove the returner block
		    //      System.out.println("stepping aside reached row reasign blocker " + i + " setter " + blocker[0] + " remover " + blocker[1]+ " row " + blocker[2]);
		    blocker = new int[3];
		    blocker[0] = ((int[])blockers.get(j))[0];                   // the asker is setter
		    blocker[1] = blocker[0];                                    // the asker is setter
		    j = ((Integer)paxinrowlist.get(paxinrowlist.size()-1)).intValue();
		    blocker[2] = pax[j][ixloc]/corcellsize[ pax[j][iprescor]];   // deepest in the row present position
		    blockers.add(blocker);
		    pax[j][iwait] = STATUS_WAITING;                                 // make deepest wait for asker
		    pax[blocker[0]][iwait] = j;
		    //      System.out.println("stepping aside reached row added blocker " + i + " setter " + blocker[0] + " remover " + blocker[1]+ " row " + blocker[2]);
		}		
	    } else if(pax[i][istat] == STATUS_MOVINGAHEAD){
	      if(pax[i][istowtime] > 0) {
		 pax[i][istat] = STATUS_STOWING;                       // hasn't stowed
	      } else {
	        pax[i][iprescor]++;                                         // go into next corridor
	        pax[i][ixloc] = 0;
		changeCorridor(i,nextpax, pax[i][iprescor]-1, pax);
	        if(pax[i][iwait] > STATUS_WAITING) {                        // if someone is waiting on this pax
	          pax[pax[i][iwait]][iwait] = STATUS_NOT_WAITING;           // set they both free
	          pax[i][iwait] = STATUS_NOT_WAITING;
	        }
	      }
	    } else if(pax[i][istat] == STATUS_STOWING) {
	      if(fitInOverheadBin(pax[i][ibagsize], pax[i][ixloc])){ // can stow without searching
		pax[i][istowtime]--;
		if(pax[i][istowtime] < 0) pax[i][istat] = STATUS_MOVINGAHEAD;
	      } else {
		pax[i][idir] = -1*pax[i][idir];
		tryToMoveInAisle(i, nextpax, pax);    // must search to stow
	      }
	    }
	} else if(pax[i][ineededrow]-1 == (pax[i][ixloc]/corcellsize[aisleindex]) &&
		  pax[i][istat] == STATUS_MOVINGAHEAD) {  // one position short on goal row
	      //
	      // check passengers in a row and
	      // that have the same row preference
	      // if so
	      // check if their present location less than the ith pax desired seat location
	      // if so
	      // add them to the list of pax in row
	      //
	      Integer ii = new Integer(i);
	      ArrayList paxinrowlist = paxinrowlist = new ArrayList();	
	      int j = 0;
	      while( j < nextpax) {
	        if(j != i && pax[j][iprescor] == rowindex && pax[j][ifrow] == pax[i][ineededrow]) {
	          if(pax[j][ineededseat] < pax[i][ineededseat] ) {
		    //		    System.out.println("Pax in the way in row " + pax[i][ineededrow] + " " +pax[j][ineededseat] + " " + pax[i][ineededseat]);
		    paxinrowlist.add(new Integer(j));
		  }
	        }
		j++;
	      }
	      //
	      // if there are pax in the way in the row
	      // arrange for them to come out and step back
	      // otherwise step into the row
	      //
	      if(!paxinrowlist.isEmpty()) {
		 if(!htmovedoccupants.contains(ii)) htmovedoccupants.put(ii,paxinrowlist); 
                // decrement the ith pax back so that it will be out of the way
	        boolean setblocker = moveRowOccupants(i, nextpax, pax, paxinrowlist);
		// set blocker in 
		int [] blocker = new int[3];
		if(setblocker){
		  blocker[0] = ((Integer)paxinrowlist.get(paxinrowlist.size()-1)).intValue(); // deepest is setter
		  blocker[1] = i;                                                // asker is remover
		  blocker[2] = (pax[i][ixloc]/corcellsize[pax[i][iprescor]]);    // askers position is row
		  if(!blockers.contains(blocker))blockers.add(blocker);
		  //      System.out.println("moving occupants new blocker " + i + " setter " + blocker[0] + " remover " + blocker[1]+ " row " + blocker[2]);
		  // make the asker wait on the deepest
		  pax[i][iwait] = STATUS_WAITING;
		  pax[((Integer)paxinrowlist.get(paxinrowlist.size()-1)).intValue()][iwait] = i;
		}
	      } else{
		//
		// no one is in the row so step forward
		//
	        if(positionEmpty(i, nextpax, pax, corindex, (pax[i][ixloc]+(corspeed[corindex]*pax[i][idir]))/corcellsize[corindex]) ) pax[i][ixloc] += corspeed[pax[i][iprescor]]*pax[i][idir];
	      }
	} else if (pax[i][istat] == STATUS_STOWING && 
                   fitInOverheadBin(pax[i][ibagsize], overheadbin[pax[i][ixloc]])) {
	  pax[i][istowtime]--;
	  if(pax[i][istowtime] < 0) pax[i][istat] = STATUS_MOVINGAHEAD;
		     //
	} else	{
	  tryToMoveInAisle(i, nextpax, pax);
	}
  }

  private static boolean tryToMoveInAisle(int i, int nextpax, int[][] pax){
    int corindex = pax[i][iprescor];
    if(!positionEmpty(i, nextpax, pax, corindex, (pax[i][ixloc]+(corspeed[corindex]*pax[i][idir]))/corcellsize[corindex])) return false;
	  if(pax[i][ineededrow] > pax[i][ixloc]/corcellsize[aisleindex]) {
	    pax[i][idir] = Math.abs(pax[i][idir]);
	    pax[i][ixloc] += corspeed[corindex]*pax[i][idir];
	  } else if(pax[i][ineededrow] < pax[i][ixloc]/corcellsize[aisleindex]) {
	    pax[i][idir] = -1 *Math.abs(pax[i][idir]);
	    pax[i][ixloc] += corspeed[corindex]*pax[i][idir];
	  }
	  return true;
  }

  private static boolean moveRowOccupants(int i,  int limit, int[][] pax,ArrayList paxinrowlist){
   int inum = 0;
   boolean bothcanreach = true;
   boolean noonecomingout = true;
   int neededposition = 0;
    //
    // determine needed position of pax in the row list
    //
    //
    // sort paxinrowlist by seat x position from aisle to window
    //
    sortPaxInRowList(pax, paxinrowlist);
    bothcanreach = true;
    for( int j = 0; j < paxinrowlist.size(); j++) {
    // can both reach their needed positions
      if(pax[((Integer)paxinrowlist.get(j)).intValue()][istat] != STATUS_COMINGOUT){
	   neededposition = neededAislePosition(j, pax, paxinrowlist);
	 if(!canReachAislePosition( pax, i, limit, neededposition)) bothcanreach = false;
      } else {
	noonecomingout = false;
      }
    }
    // if so
    if(bothcanreach) {
      for( int j = 0; j < paxinrowlist.size(); j++) {
        inum = ((Integer)paxinrowlist.get(j)).intValue();
	if(pax[inum][istat] != STATUS_COMINGOUT){
          // set their stati to comingout
	  pax[inum][istat] = STATUS_COMINGOUT;
          // set their speeds to negative
          pax[inum][idir] = -1;
          // set their desired rows
          pax[inum][ineededrow] = neededAislePosition(j, pax, paxinrowlist);
	  seatedpax.remove(new Integer(inum));
	  totalneedtocomeout++;
	}
      }
    }  
    return (noonecomingout && bothcanreach);
  }

  private static boolean positionEmpty(int inum, int limit, int[][] pax, int corindex, int iposition) {
    boolean empty = true;
    int i = 0;
    System.out.println("positionEmpty " +  inum + " " + limit + " " +  corindex + " " + iposition );
    if(corindex != rowindex){
      while(i < limit && empty) {
        if(inum != i && pax[i][iprescor] == corindex && pax[i][ixloc]/corcellsize[ pax[i][iprescor]] == iposition) empty = false;
	System.out.println("positionEmpty " +  i + " " +pax[i][iprescor] + " " + pax[i][ixloc]/corcellsize[pax[i][iprescor]] + " " + empty );
        i++;
      }
    } else {
      while(i < limit && empty) {
        if(inum != i && 
	    pax[i][iprescor] == corindex && 
	    pax[i][ixloc]/corcellsize[corindex] == iposition &&
	    pax[i][ifrow] == pax[inum][ifrow]
	   ) empty = false;
	//       System.out.println("positionEmpty " +   pax[i][iprescor] + " " + pax[i][ixloc]/corcellsize[pax[i][iprescor]] + " " + empty );
        i++;
      }
    }
    if(empty) {
      i = 0;
      while(i < blockers.size() && empty) {
	if(corindex == aisleindex && 
	   ((int[])blockers.get(i))[0] != inum && 
	   ((int[])blockers.get(i))[2] == iposition){
          empty = false;
	  /*
        System.out.println("positionEmpty blockers " +  ((int[])blockers.get(i))[0]  + " " + 
			   ((int[])blockers.get(i))[1] + " " + 
			   ((int[])blockers.get(i))[2] + " " + 
			   empty );
	  */
	}
	i++;
      }
      
    }
    return empty;
  }

  private static boolean positionsEmpty(int inum, int limit, int[][] pax, int corindex, int ibposition, int ieposition) {
    boolean empty = true;
    int i = 0;
    int j = ibposition;
    //   System.out.println("positionEmpty " +  inum + " " + limit + " " +  corindex + " " + ibposition  + " " + ieposition  );
    if(corindex != rowindex){
      while(j <= ieposition && empty) {
        i = 0;
        while(i < limit && empty) {
          if(inum != i && pax[i][iprescor] == corindex && pax[i][ixloc]/corcellsize[ pax[i][iprescor]] == j) empty = false;
	  //          System.out.println("positionEmpty " +  i + " " +pax[i][iprescor] + " " + pax[i][ixloc]/corcellsize[pax[i][iprescor]] + " " + empty );
          i++;
        }
	j++;
      }
    } else {
      while(j <= ieposition && empty) {
        i = 0;
        while(i < limit && empty) {
          if(inum != i && 
	    pax[i][iprescor] == corindex && 
	    pax[i][ixloc]/corcellsize[corindex] == j &&
	    pax[i][ifrow] == pax[inum][ifrow]
	   ) empty = false;
	  //          System.out.println("positionEmpty " +   pax[i][iprescor] + " " + pax[i][ixloc]/corcellsize[pax[i][iprescor]] + " " + empty );
          i++;
        }
	j++;
      }
    }
    if(empty) {
      while(j <= ieposition && empty) {
        i = 0;
        while(i < blockers.size() && empty) {
	  if(corindex == aisleindex && 
	   ((int[])blockers.get(i))[0] != inum && 
	   ((int[])blockers.get(i))[2] == j){
            empty = false;
	    /*
            System.out.println("positionEmpty blockers " +  ((int[])blockers.get(i))[0]  + " " + 
			   ((int[])blockers.get(i))[1] + " " + 
			   ((int[])blockers.get(i))[2] + " " + 
			   empty );
	    */
	  }
	  i++;
        }
	j++;
      }      
    }
    return empty;
  }

  /**
   * @param j element in the paxinrowlist
   * @param p passengers
   * @param paxinrowlist list of pax in row that must come out
   * @return the position in the aisle
   */
  private static int neededAislePosition(int j,  int[][]p, ArrayList paxinrowlist) {
    int inum = ((Integer)paxinrowlist.get(j)).intValue();
    return p[inum][ifrow] + paxinrowlist.size() - j;
  }

  /**
   * if there is a passenger standing in a row position in the aisle at the row exit 
   * or anywhere up to and including the stand aside position in the aisle
   * the given passengers can not reach their stand aside position
   * @param pax the array of passengers
   * @param i the number of the passenger asking (in the aisle at the row position)
   * @param limit
   * @param neededposition poistion in aisle needed by coming out
   */
  private static boolean canReachAislePosition( int[][] pax, int i, int limit, int neededposition) {
    int m = 0;
    boolean occupied = false;
    int irowpos = pax[i][ixloc]/corcellsize[pax[i][iprescor]];
    //    System.out.println("canReachAislePosition " + neededposition + " " + irowpos);
    while(m < limit && !occupied) {
      if(m != i &&
	 pax[m][iprescor] == aisleindex && 
	 irowpos <= pax[m][ixloc]/corcellsize[pax[m][iprescor]] &&
	 pax[m][ixloc]/corcellsize[pax[m][iprescor]]  <= neededposition
	 ) {
	    occupied = true;
	    //            System.out.println("canReachAislePosition " + occupied);
	    //    	    writeOutPax(m, pax);
        }
      m++;
    }
    return !occupied;
  }

    //
    // sort paxinrowlist by seat x position from aisle to window
    //
  private static void sortPaxInRowList(int[][] pax, ArrayList paxinrowlist){
    Integer itmp = null;
    for (int j = 0; j <paxinrowlist.size()-1; j++){
      for (int i = 0; i < paxinrowlist.size()-1; i++){
        if(pax[((Integer)paxinrowlist.get(i)).intValue()][ineededseat] > pax[((Integer)paxinrowlist.get(i+1)).intValue()][ineededseat]) {
	  itmp = (Integer)paxinrowlist.get(i);
	  paxinrowlist.set(i,paxinrowlist.get(i+1));
	  paxinrowlist.set(i+1,itmp);	  
        }
      }
    }
 }

  private static boolean fitInOverheadBin(int ibagsize, int overheadbinindex){
    return true;
  }

  private static void changeCorridor( int i, int nextpax, int prevcorindex, int[][] pax) {
    int iposition = 0;
    Conduit prevsobj = null;
    Passenger passenger = (Passenger)alpax.get(i);
    if(pax[i][iprescor] == entryindex) {          // is now in the entry way 
      acentryway.fitInMovingObject((MovingObject)passenger, false);
      passenger.c.cctr.y = acentryway.getWidth()/2;
    } else if (pax[i][iprescor] == aisleindex) {  // is now in  the aisle
      if(prevcorindex == entryindex) {
	prevsobj = acentryway;
        acentryway.extractMovingObject((MovingObject)passenger);
      } else if (prevcorindex == rowindex) {
	prevsobj = (Conduit)(pax[i][iacseat] < 3 ? alLeftRows.get(pax[i][iacrow]) : alRightRows.get(pax[i][iacrow]));
        ((Corridor)prevsobj).extractMovingObject((MovingObject)passenger) ;
	passenger.setMovingSideways(false);
      }
      acaisle.fitInMovingObject((MovingObject)passenger, false);
      passenger.c.cctr.x = acaisle.getClosestCommonOpening((MovingObject)passenger, prevsobj).getPortal(acaisle).getCenterX();
      passenger.c.cctr.y = acaisle.getWidth()/2;
      pax[i][ixloc] = (int)passenger.c.cctr.x;
      iposition = pax[i][ixloc]/corcellsize[aisleindex];
      while(!positionEmpty(i, nextpax, pax, aisleindex, iposition)) iposition--;
      pax[i][ixloc] = iposition*corcellsize[aisleindex];
    } else if (pax[i][iprescor] == rowindex) {   // is now in a row
      acaisle.extractMovingObject((MovingObject)passenger);
      acrowway = (ACRowWay)(pax[i][iacseat] < 3 ? alLeftRows.get(pax[i][iacrow]) : alRightRows.get(pax[i][iacrow]));
      System.out.println(i + " " + pax[i][iacrow] + " " + pax[i][iacseat] + " " + acrowway.getName());
      acrowway.fitInMovingObject((MovingObject)passenger, false) ;
      passenger.setMovingSideways(true);
      passenger.c.cctr.y = acrowway.getWidth()/2;
    } else if (pax[i][iprescor] == seatindex) {   // is now in a seat
    } 
  }
  
  private static void setPassenger(int i, Conduit sobj, ArrayList alpax, int[][] pax) {
      passenger = (Passenger)alpax.get(i);
      passenger.c.cdx = corspeed[pax[i][iprescor]];
      passenger.c.cdy = SimObject.ZERO;
      passenger.c.pdx =  passenger.c.cdx;
      passenger.c.pdy =  passenger.c.cdy;
      passenger.c.cctr.x =  pax[i][ixloc];
      passenger.c.cctr.y = sobj.getWidth()/2 ;
      passenger.c.pctr.x = passenger.c.cctr.x - passenger.c.pdx;
      passenger.c.cctr.y = passenger.c.cctr.y;
    }

  private static void  updateSimDisplay(Vector vtsobjs){
	for(int i = 0; i < vtsobjs.size(); i++) {
	  Conduit sobj = (Conduit)vtsobjs.elementAt(i);
	  Vector vmobjs = sobj.getMovingObjects();
	  if(vmobjs != null && vmobjs.size() > 0) {
	    simdisp.update(sobj,sobj);
	  }	  
	}
  }

  private static void  createRowArrays(Vector vtsobjs){
    alLeftRows.add(0,new ACRowWay());
    alRightRows.add(0,new ACRowWay());
    for(int i = 0; i < vtsobjs.size(); i++) {
      Conduit sobj = (Conduit)vtsobjs.elementAt(i);
      String name = sobj.getName();
      if(name.startsWith("Row")){
	Integer integer = new Integer(name.substring(name.length()-3,name.length()-1));
        if(name.endsWith("L")){
	  alLeftRows.add(integer.intValue(),sobj);
	} else {
	  alRightRows.add(integer.intValue(),sobj);
	}	
      }
    }
  }

  private static void	writeOutPax(int i, int[][] pax){
	System.out.println(i + " " + 0 + " present corridor " + pax[i][0]);
 	System.out.println(i + " " + ineededrow + " needed row " + pax[i][ineededrow]);
	System.out.println(i + " " + ineededseat + " needed seat element " + pax[i][ineededseat]+" needed seat position " + pax[i][ineededseat]);
	System.out.println(i + " " + ixloc + " x position " + pax[i][ixloc]);
	System.out.println(i + " " + istat + " status " + pax[i][istat]);
	System.out.println(i + " " + idir + " direction " + pax[i][idir]);
	System.out.println(i + " " + ifrow + "final row " + pax[i][ifrow]);
  }

  private static void	writeOutPax(int inum, int[][] pax, int nextpax){
    int i = 0;
    System.out.println(inum + " " + nextpax);
    while(i < nextpax) {
	System.out.println(i + " " + iprescor + " present corridor " + pax[i][iprescor]);
 	System.out.println(i + " " + ineededrow + " needed row " + pax[i][ineededrow]);
	System.out.println(i + " present seat element " + ineededseat +" needed seat position " + pax[i][ineededseat]);
	System.out.println(i + " " + ixloc + " x location " + pax[i][ixloc] + " x position " + pax[i][ixloc]/corcellsize[pax[i][iprescor]]);
	System.out.println(i + " " + istat + " status " + pax[i][istat]);
	System.out.println(i + " " + idir + " direction " + pax[i][idir]);
	System.out.println(i + " " + ifrow + " final row " + pax[i][ifrow]);
	System.out.println(i + " " + iwait + " wait status " + pax[i][iwait]);
	i++;
    }
  }

  public static int[][] openSeatingFtoBFullRows(int totalpax) {
    int[][]pax = new int[137][NUM_PROPERTIES];  // number, cornum, row, seat, x, status, direction, final row, waiting(-2,-1 or waitee inum)
    // first row
    // since the left and right sides are offset
    // the needed row positions for the two halves are separated by 2
    //
    int num = 0;
    for (int i = 0; i <6; i++) {
      pax[num][iprescor] = 0;
      pax[num][ineededrow] = (((i/3)+0+1)*aislecellsperrow) - 1-(i/3);
      pax[num][ineededseat]= i-((i/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      pax[num][ifrow]= (((i/3)+0+1)*aislecellsperrow) - 1-(i/3);
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][istowtime]= STOW_SECONDS;
      pax[num][iacrow]= 1;
      pax[num][iacseat]= i+1;
      num++;
    }
    //
    // rows 2 through 10
    //
    for (int j = 2-1; j <10; j++) {
      for (int i = 0; i <6; i++) {
      pax[num][iprescor] = 0;
      pax[num][ineededrow]= (((i/3)+j+1)*aislecellsperrow) - 1-(i/3);
      pax[num][ineededseat]= i-((i/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      //      pax[num][ifrow]= ((j+1)*aislecellsperrow) - 1;
      pax[num][ifrow]= (((i/3)+j+1)*aislecellsperrow) - 1-(i/3) ;
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][istowtime]= STOW_SECONDS;
      pax[num][iacrow]= j+1;
      pax[num][iacseat]= i+1;
        num++;
      }
    }
    // 12 through 22
    //
    for (int j = 12-1; j < 22; j++) {
      for (int i = 0; i <6; i++) {
      pax[num][iprescor] = 0;
      pax[num][ineededrow]= (((i/3)+j+1)*aislecellsperrow) - 1-(i/3);
      pax[num][ineededseat]= i-((i/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      pax[num][ifrow]= (((i/3)+j+1)*aislecellsperrow) - 1-(i/3);
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][istowtime]= STOW_SECONDS;
      pax[num][iacrow]= j+1;
      pax[num][iacseat]= i+1;
      num++;
      }
    }
    // row 11
    //
    for (int i = 0; i <5; i++) {
      pax[num][iprescor] = 0;
      pax[num][ineededrow]= (((i/3)+11-1+1)*aislecellsperrow) - 1-(i/3);
      pax[num][ineededseat]= i-((i/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      pax[num][ifrow]= (((i/3)+11-1+1)*aislecellsperrow) - 1-(i/3);
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][istowtime]= STOW_SECONDS;
      pax[num][iacrow]= 11;
      pax[num][iacseat]= i+1;
      num++;
    }
    // row 23
    //
    for (int i = 0; i <6; i++) {
      pax[num][iprescor] = 0;
      pax[num][ineededrow]= (((i/3)+23-1+1)*aislecellsperrow) - 1-(i/3);
      pax[num][ineededseat]= i-((i/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      pax[num][ifrow]= (((i/3)+23-1+1)*aislecellsperrow) - 1-(i/3);
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][istowtime]= STOW_SECONDS;
      pax[num][iacrow]= 23;
      pax[num][iacseat]= i+1;
      num++;
    }
    return pax;
  }

  public static int[][] openSeatingPreferredShortHaul(int totalpax) {
    int[][] seatpreference1 = {{0,3,2,5,1,4,3,0,5,2,3,0,5,2,3,0,3,0,3,0,3,0,3,0,3,0,3,0},
			       {4,1,4,1,5,2,5,2,5,2,5,2,5,2,5,2,5,2, 3, 0, 3, 0, 5, 3, 0},
			       {4,1,4,1,4,1,4,1,4,1,4,1,4,1, 4, 1, 2, 3, 0, 3, 0, 3, 0, 3, 0, 3, 0, 3, 0, 3},
			       { 2, 5, 2, 5, 2, 5, 2, 5, 2, 5, 2, 5, 2, 5, 0, 3, 0, 3, 0},
			       { 4, 1, 4, 1, 4, 1, 4, 1, 4, 2, 5, 2, 5, 2, 5, 2, 3, 0},
			       { 1, 4, 1, 4, 1, 4, 1, 4, 1, 4, 1, 4, 1, 5, 2},
			       { 4, 1}};

    int[][] rowpreference1  = {{0,0,0,0,0,0,1,1,1,1,2,2,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9},
			       {1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,11,12,12},
			       {3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,10,13,13,14,14,15,15,16,16,17,17,18,18,19},
			       {11,12,12,13,13,14,14,15,15,16,16,17,17,18,19,20,20,21,21},
			       {11,11,12,12,13,13,14,14,15,18,19,19,20,20,21,21,22,22},
			       {15,16,16,17,17,18,18,19,19,20,20,21,21,22,22},
                               {22,22}};

    int[] index = {28,25,30,19,18,15,2};
    int[] seatpreference = new int[137];
    int[]  rowpreference = new int[137];
    int num = 0;
    for(int j = 0; j < 7; j++) {
      for( int i = 0; i < index[j]; i++) {
        seatpreference[num] = seatpreference1[j][i];
        rowpreference[num] = rowpreference1[j][i];
	num++;
      }    
    }

      //    int[] seatpreference = {0,3,2,5,1,4}; 
			  // labels C,D,A,F,B,E elements 0,1,2,3,4,5
    int[][]pax = new int[137][NUM_PROPERTIES];  // number, cornum, row, seat, x, status, direction, final row, waiting(-2,-1 or waitee inum)
    // first row
    // since the left and right sides are offset
    // the needed row positions for the two halves are separated by 2
    //
        for (num = 0; num <137; num++) {
          System.out.println( (num+1) + " " +" seat " + (1+rowpreference[num]) + seatlabels[seatpreference[num]] );
       }
    
    int m = 0;
    int k = 0;
    for (num = 0; num <totalpax; num++) {
      k = seatpreference[num];
      m = rowpreference[num];
      pax[num][iprescor] = 0;
      pax[num][ineededrow] = (((k/3)+m+1)*aislecellsperrow) - 1-(k/3);
      pax[num][ineededseat]= k-((k/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      pax[num][ifrow]= (((k/3)+m+1)*aislecellsperrow) - 1-(k/3);
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][istowtime]= STOW_SECONDS;
      pax[num][iacrow]= m+1;
      pax[num][iacseat]= k+1;
    }
    return pax;
  }


  public static int[][] openSeatingPreferredLongHaul(int totalpax) {
    int[] seatpreference = {0,3,2,5,1,4}; // labels C,D,A,F,B,E elements 0,1,2,3,4,5
    int[][]pax = new int[137][NUM_PROPERTIES];  // number, cornum, row, seat, x, status, direction, final row, waiting(-2,-1 or waitee inum)
    // first row
    // since the left and right sides are offset
    // the needed row positions for the two halves are separated by 2
    //
    int num = 0;
    int k = 0;
    for (int i = 0; i <6; i++) {
      k = seatpreference[i];
      pax[num][iprescor] = 0;
      pax[num][ineededrow] = (((k/3)+0+1)*aislecellsperrow) - 1-(k/3);
      pax[num][ineededseat]= k-((k/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      pax[num][ifrow]= (((k/3)+0+1)*aislecellsperrow) - 1-(k/3);
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][istowtime]= STOW_SECONDS;
      pax[num][iacrow]= 1;
      pax[num][iacseat]= k+1;
      num++;
    }
    //
    // rows 2 through 22
    //
    for (int j = 2-1; j <22; j++) {                             // for each row 2 through 22
      for (int i = 0; i <2; i++) {                                  // for each seat C, D
        if(j != 9) {                          // skip row 10
          k = seatpreference[i];
          pax[num][iprescor] = 0;
          pax[num][ineededrow]= (((k/3)+j+1)*aislecellsperrow) - 1-(k/3);
          pax[num][ineededseat]= k-((k/3)*3);
          pax[num][ixloc]= 0;
          pax[num][istat]= 0;
          pax[num][idir]= 1;
      //      pax[num][ifrow]= ((j+1)*aislecellsperrow) - 1;
          pax[num][ifrow]= (((k/3)+j+1)*aislecellsperrow) - 1-(k/3) ;
          pax[num][iwait]= STATUS_NOT_WAITING;
          pax[num][istowtime]= STOW_SECONDS;
          pax[num][iacrow]= j+1;
          pax[num][iacseat]= k+1;
          num++;
        }
      }
    }
    for (int j = 2-1; j <22; j++) {                             // for each row 2 through 22
      for (int i = 2; i <4; i++) {                                  // for each seat A, F
        if((j == 10 && i == 3) ||  j == 9) {  
        } else {                        // skip row 11 seat F and row 10
          k = seatpreference[i];
          pax[num][iprescor] = 0;
          pax[num][ineededrow]= (((k/3)+j+1)*aislecellsperrow) - 1-(k/3);
          pax[num][ineededseat]= k-((k/3)*3);
          pax[num][ixloc]= 0;
          pax[num][istat]= 0;
          pax[num][idir]= 1;
      //      pax[num][ifrow]= ((j+1)*aislecellsperrow) - 1;
          pax[num][ifrow]= (((k/3)+j+1)*aislecellsperrow) - 1-(k/3) ;
          pax[num][iwait]= STATUS_NOT_WAITING;
          pax[num][istowtime]= STOW_SECONDS;
          pax[num][iacrow]= j+1;
          pax[num][iacseat]= k+1;
          num++;
        }
      }
    }
    for (int j = 2-1; j <22; j++) {                             // for each row 2 through 22
      for (int i = 4; i <6; i++) {                                  // for each seat B, E
        if(j != 9) {                          // skip row 10
          k = seatpreference[i];
          pax[num][iprescor] = 0;
          pax[num][ineededrow]= (((k/3)+j+1)*aislecellsperrow) - 1-(k/3);
          pax[num][ineededseat]= k-((k/3)*3);
          pax[num][ixloc]= 0;
          pax[num][istat]= 0;
          pax[num][idir]= 1;
      //      pax[num][ifrow]= ((j+1)*aislecellsperrow) - 1;
          pax[num][ifrow]= (((k/3)+j+1)*aislecellsperrow) - 1-(k/3) ;
          pax[num][iwait]= STATUS_NOT_WAITING;
          pax[num][istowtime]= STOW_SECONDS;
          pax[num][iacrow]= j+1;
          pax[num][iacseat]= k+1;
          num++;
        }
      }
    }
    // row 10
    //
    for (int i = 0; i <6; i++) {
      k = seatpreference[i];
      pax[num][iprescor] = 0;
      pax[num][ineededrow]= (((k/3)+10-1+1)*aislecellsperrow) - 1-(k/3);
      pax[num][ineededseat]= k-((k/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      pax[num][ifrow]= (((k/3)+10-1+1)*aislecellsperrow) - 1-(k/3);
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][iacrow]= 10;
      pax[num][iacseat]= k+1;
      num++;
    }
   // row 23
    //
    for (int i = 0; i <6; i++) {
      k = seatpreference[i];
      pax[num][iprescor] = 0;
      pax[num][ineededrow]= (((k/3)+23-1+1)*aislecellsperrow) - 1-(k/3);
      pax[num][ineededseat]= k-((k/3)*3);
      pax[num][ixloc]= 0;
      pax[num][istat]= 0;
      pax[num][idir]= 1;
      pax[num][ifrow]= (((k/3)+23-1+1)*aislecellsperrow) - 1-(k/3);
      pax[num][iwait]= STATUS_NOT_WAITING;
      pax[num][istowtime]= STOW_SECONDS;
      pax[num][iacrow]= 23;
      pax[num][iacseat]= k+1;
      num++;
    }
    for(num = 0; num < totalpax; num++) {
      System.out.println("openlongpax " + num + " " + pax[num][ifrow] + " " + seatlabels[pax[num][ineededseat]] );
    }
    return pax;
  }

  public static int[][] assignedSeatingByRowPreferredLongHaul(int totalpax) {
    int[] seatpreference = {0,3,2,5,1,4}; // labels C,D,A,F,B,E elements 0,1,2,3,4,5
    int[][]pax = new int[137][NUM_PROPERTIES];  // number, cornum, row, seat, x, status, direction, final row, waiting(-2,-1 or waitee inum)
    // first row
    // since the left and right sides are offset
    // the needed row positions for the two halves are separated by 2
    //
    int num = 0;
    int k = 0;
    int m = 0;
    int n = 0;
    Random rand = new Random();
    //
    // deepest to shallowest row for number of passengers
    //
    n = 1+(totalpax/6);
    if(n*6 < totalpax) n++;
    int j = n;
    while (j >=0 && num <= totalpax ) {                         
      k = (int)(rand.nextDouble()*5.999);
      m = 0;
      while (m < 6) {                           
	if((j != 10 || k != 2)) {                              // skip 11F     not present on 700            
          pax[num][iprescor] = 0;
          pax[num][ineededrow]= (((k/3)+j+1)*aislecellsperrow) - 1-(k/3);
          pax[num][ineededseat]= k-((k/3)*3);
          pax[num][ixloc]= 0;
          pax[num][istat]= 0;
          pax[num][idir]= 1;
          pax[num][ifrow]= (((k/3)+j+1)*aislecellsperrow) - 1-(k/3) ;
          pax[num][iwait]= STATUS_NOT_WAITING;
          pax[num][istowtime]= STOW_SECONDS;
          pax[num][iacrow]= j+1;
          pax[num][iacseat]= k+1;
	  //	  System.out.println("row " +  pax[num][ineededrow] + " seat " +  pax[num][ineededseat]);
          num++;
	}
	m++;
	k++;
	if(k > 5) k = 0;                  
      }
      j--;
    }
     return pax;
  }

  
  public static int[][] assignedSeatingByRowPreferredShortHaul(int totalpax) {
    //    int[] startroworder = {17,1,8};
    //    int[] endroworder = {23,7,16};
    int[] startroworder = {16,8,1};
    int[] endroworder = {23,15,7};
    int[][]pax;
    pax = openSeatingPreferredShortHaul(totalpax);
    randomize(pax);
    //
    //TO DO
    //
    int[][]rowpax = new int[137][NUM_PROPERTIES];
    int num = 0;
    // sort the pax by row groups;
    for(int i = 0; i < startroworder.length; i++) {
      for(int j = 0; j < totalpax; j++) {      
	if(pax[j][iacrow] >= startroworder[i] && pax[j][iacrow] <= endroworder[i]){
	  for(int k = 0; k < NUM_PROPERTIES; k++) {
	    rowpax[num][k] = pax[j][k]; 
	  }
	  num++;
	}
      }
    }
    for(num = 0; num < totalpax; num++) {
      System.out.println("rowshortpax " + (num+1) + " " + rowpax[num][iacrow] + seatlabels[rowpax[num][iacseat]-1] );
    }
    return rowpax;
  }

  public static int[][] assignedSeatingBySeatPreferredLongHaul(int totalpax) {
    //    int[] seatorder = {0,3,2,5,1,4}; // labels C,D,A,F,B,E elements 0,1,2,3,4,5
    int[] seatorder = {5,2,4,1,3,0}; // labels C,B,A,D,E,F elements 0,1,2,3,4,5
    int[][]pax;
    
    //
    pax = openSeatingPreferredLongHaul(totalpax);
    int num = -1;
    int[][]wilmapax = new int[137][NUM_PROPERTIES];
    // sort the pax by seats F, A, E, B, D, and C;
    for(int i = 0; i < seatorder.length; i++) {
     for(int j = 0; j < totalpax; j++) {      
       if(pax[j][ineededseat] == seatorder[i]){
	 num++;
	 for(int k = 0; k < NUM_PROPERTIES; k++) {
	   wilmapax[num][k] = pax[j][k]; 
	 }
       }
    }
     
    }
    for(num = 0; num < totalpax; num++) {
      System.out.println("wilmalongpax " + (num+1) + " " + wilmapax[num][iacrow] + " " + seatlabels[wilmapax[num][iacseat]-1] );
    }
     return wilmapax;
  }

  public static int[][] assignedSeatingBySeatPreferredShortHaul(int totalpax) {
    //    int[] seatorder = {0,3,2,5,1,4}; // labels C,D,A,F,B,E elements 0,1,2,3,4,5
    int[] seatorder = {5,2,4,1,3,0}; // labels C,B,A,D,E,F elements 0,1,2,3,4,5
    int[][]pax;
    
    //
    pax = openSeatingPreferredShortHaul(totalpax);
    randomize(pax);
    int num = -1;
    int[][]wilmapax = new int[137][NUM_PROPERTIES];
    // sort the pax by seats F, A, E, B, D, and C;
    for(int i = 0; i < seatorder.length; i++) {
     for(int j = 0; j < totalpax; j++) {      
       if(pax[j][ineededseat] == seatorder[i]){
	 num++;
	 for(int k = 0; k < NUM_PROPERTIES; k++) {
	   wilmapax[num][k] = pax[j][k]; 
	 }
       }
    }
     
    }
    for(num = 0; num < totalpax; num++) {
      System.out.println("wilmashortpax " + (num+1) + " " + wilmapax[num][iacrow] + seatlabels[wilmapax[num][iacseat]-1] );
    }
     return wilmapax;
  }

  /**
   * the returned array has the aisle pax in the latter part of the array
   */
  public static int[][] openSeatingByZonePreferredShortHaul(int totalpax){
    int num = 0;
    int wmanum = 0;
    int[][] wmapax;
    int[][] pax = openSeatingPreferredShortHaul(totalpax);
    return sortWMnA(pax, totalpax);
  }

  /**
   * the returned array has the aisle pax in the latter part of the array
   */
  public static int[][] openSeatingByZonePreferredLongHaul(int totalpax){
    int[][] wmapax;
    int[][] pax = openSeatingPreferredLongHaul(totalpax);
    for(int num = 0; num <totalpax ; num++) {
      System.out.println("pax " +  num+ " " + pax[num][ifrow] + " " + pax[num][ineededseat] );
    }
    return sortWMnA(pax,totalpax);
  }

  private static int[][] sortWMnA(int[][] pax, int totalpax){
    int num = 0;
    int wmanum = 0;
    int grsize = 137/3;
    int grnum =3;
    int i,j,k;
    int[][] wmapax = new int[137][NUM_PROPERTIES];
    // store the pax neither in seat C nor in seat D
    for (i = 0; i < grnum; i++) {
      j = i*grsize;
      k = (i+1)*grsize;
      if (k > totalpax) k = totalpax;
      for (num = j; num < k; num++) {
        if(pax[num][ineededseat] != 0) {
          wmapax[wmanum][iprescor] = pax[num][iprescor];
          wmapax[wmanum][ineededrow] = pax[num][ineededrow];
          wmapax[wmanum][ixloc] = pax[num][ixloc];
          wmapax[wmanum][istat] = pax[num][istat];
          wmapax[wmanum][idir] = pax[num][idir];
          wmapax[wmanum][ifrow] = pax[num][ifrow];
          wmapax[wmanum][iwait] = pax[num][iwait];
          wmapax[wmanum][iwait] = pax[num][iwait];
          wmapax[wmanum][istowtime] = pax[num][istowtime];
	  wmapax[wmanum][ineededseat]= pax[num][ineededseat];
          wmapax[num][iacrow]= pax[num][iacrow];
          wmapax[num][iacseat]= pax[num][iacseat];
	  wmanum++;
        }
      }
      // store the pax either in seat C or in seat D
      for (num = j; num < k; num++) {
        if(pax[num][ineededseat] == 0) {
          wmapax[wmanum][iprescor] = pax[num][iprescor];
          wmapax[wmanum][ineededrow] = pax[num][ineededrow];
          wmapax[wmanum][ixloc] = pax[num][ixloc];
          wmapax[wmanum][istat] = pax[num][istat];
          wmapax[wmanum][idir] = pax[num][idir];
          wmapax[wmanum][ifrow] = pax[num][ifrow];
          wmapax[wmanum][iwait] = pax[num][iwait];
          wmapax[wmanum][istowtime] = pax[num][istowtime];
	  wmapax[wmanum][ineededseat]= pax[num][ineededseat];
          wmapax[num][iacrow]= pax[num][iacrow];
          wmapax[num][iacseat]= pax[num][iacseat];
	  wmanum++;
        }
      }
    }
    for(num = 0; num < wmanum; num++) {
      System.out.println("wmapax " + num + " " + wmapax[num][ifrow] + " " + seatlabels[wmapax[num][ineededseat]] );
    }
    return wmapax;
  }

  /**
   * randomize passenger array
   */
  public static void  randomize(int pax[][]){
    double range = pax.length-1;
    int m = pax.length*20;
    int k, n, itemp;
    int i = 0;
    while( i < m) {
      k = (int)(0.5+range*random.nextDouble());
      n = (int)(0.5+range*random.nextDouble());
      for(int j = 0; j < NUM_PROPERTIES; j++) {
	itemp = pax[k][j]; 
	pax[k][j] = pax[n][j];
	pax[n][j] = itemp;
      }
      i++;
    } 
  }

  /**
   * given an array of pax with itpax through pax and totalpax elements filled
   * rearranges randomly selected itpax number of elements to the front of the occuppied part of the array
   * and resets the selected pax to seated
   * @param itpax number of through passengers
   * @param pax array of cellular automaton simulation
   * @param totalpax number of passengers onboard
   */
  public static void placeThruPassengers(int itpax, int[][] pax, int totalpax){
    seatedpax = new ArrayList();
    int[] tmppax = new int[NUM_PROPERTIES];
    //
    // while the number of elements selected is less than the number of through passengers
    // randomly select an element from the pax array 
    // compress the array above that element
    // store the selected element at the front of the array
    // 
    int i = 0;
    int j = 0;
    int k = 0;
    int m = 0;
    while( i < itpax-1) {
      k = (int)((double)totalpax*random.nextDouble());
      pax[k][iprescor] =  rowindex;
      pax[k][istat] = STATUS_MOVINGAHEAD;
      pax[k][ixloc] = corcellsize[pax[k][iprescor]]*pax[k][ineededseat] + corcellsize[pax[k][iprescor]]/2;
      seatedpax.add(new Integer(k));
      for(j = 0; j <pax[k].length; j++){tmppax[j] = pax[k][j];}
      for(m = k; m > 0; m--) {for(j = 0; j <pax[m].length; j++){pax[m][j] = pax[m-1][j];}}
      for(j = 0; j < pax[0].length; j++){pax[0][j] = tmppax[j];}      
      i++;
    }
  }

  public void formBookingGroups(int[][] pax, int totalpax){
    ArrayList alpaxselected = new ArrayList();
    //
    // randomly select the first person in group
    // make sure the group size is correct
    //
    int k = 0;
    int i = 1;
    int inum = 0;
    int ir = 0;
    for(i = 1; i < paxpercentingroup.length; i++) {
      inum = (int)(paxpercentingroup[i]*(double)totalpax);
      ir = inum%i;
      inum += (ir > 0.5*(double)i ? i+1 : 0);
      if(inum > 0){
	if( i == 1) {
	  doubleGroup(alpaxselected, inum, pax, totalpax);
	} else if(i ==2) {
	  tripleGroup(alpaxselected, inum, pax, totalpax);
	} else if(i == 3) {
	  quadrupleGroup(alpaxselected, inum, pax, totalpax);	  
	} else if(i == 4) {
	  quintupleGroup(alpaxselected, inum, pax, totalpax);	  
	}
      }
    }
  }

  private void  quintupleGroup(int inum, int[][]pax, int totalpax){
   quintupleGroup(new ArrayList(),inum, pax, totalpax);
  }

  private void quintupleGroup(ArrayList alpaxselected, int inum, int[][] pax, int totalpax){
    doubleGroup(alpaxselected, inum/2, pax, totalpax); 
    tripleGroup(alpaxselected, inum/2, pax, totalpax); 
  }	  
  
  private void  quadrupleGroup(int inum, int[][]pax, int totalpax){
   quadrupleGroup(new ArrayList(),inum, pax, totalpax);
  }

  private void  quadrupleGroup(ArrayList alpaxselected, int inum, int[][] pax, int totalpax){
    doubleGroup(alpaxselected, inum/2, pax, totalpax); 
    doubleGroup(alpaxselected, inum/2, pax, totalpax); 
  }

  private void  doubleGroup(int inum, int[][]pax, int totalpax){
   doubleGroup(new ArrayList(),inum, pax, totalpax);
  }

  /**
   * given the number of pax needed to form groups of two
   * select indivduals from the present population
   * to rearrange their order so they'll board the aircraft together
   * @param alpaxselected
   * @param inum the number of  pax needed to form groups
   * @param pax the array of ordered passengers
   * @param totalpax the total number of passengers
   */
  private void  doubleGroup(ArrayList alpaxselected, int inum, int[][]pax, int totalpax){
    int[] displpax = new int[NUM_PROPERTIES];
    int[] comppax = new int[NUM_PROPERTIES];
    Integer ii = null;
    Integer kk = null;
    Integer jj = null;
    int k = 0;
    int m = 0;
    int mm = 0;
    int j = 0;
    int isteps = 0;
    int isign = 0;
    int iend = 0;
    int icount = 0;
    int soughtseat = 0;
    int i = -1;
    boolean found = false;

    for(int n = 1; n <= inum/2; n++){
      soughtseat = 0;
      k = (int)(random.nextDouble()*(double)totalpax);
      kk = new Integer(k);
      while(alpaxselected.contains(kk) && alpaxselected.size() < totalpax){
	k =(int)(random.nextDouble()*(double)totalpax); 
	kk = new Integer(k);
      }
      alpaxselected.add(kk);
      m = k-1;
      soughtseat = pax[k][ineededseat]-1;
      if(soughtseat < 0) {
        soughtseat = 1;
        m = k+1;
      }
      i = -1;
      found = false;
      //
      // find a pax with the right seat
      //
      while(i < totalpax && !found){
        i++;
	ii = new Integer(i);
        if(i != k  && !alpaxselected.contains(ii)) {
	  if(pax[i][ineededseat] == soughtseat) found = true;
        }
      }
      if(found) {
	//
	// save the companion
	//
        for(j = 0; j < pax[0].length; j++){displpax[j] = pax[m][j];}
        for(j = 0; j < pax[i].length; j++){comppax[j] = pax[i][j];}
	isteps = Math.abs(k - i); // number of step between the selected and companion pax
	isign = (k - i)/isteps; // search direction for finding the end of the companions seating group
	j = i;
	icount = 0;
        found = false;
	while(icount <= isteps){
          for(mm = 0; mm < pax[mm].length; mm++){pax[j][mm] = pax[j+isign][mm];} // shift pax so the displaced pax has a place to go
	  jj = new Integer(j);
          if(j != k && !alpaxselected.contains(jj)) {
	    if(pax[j][ineededseat] == soughtseat) {found = true; iend = j+(isign*-1);} // found end of seating group
          }
	  j += isign;
	  icount++;
	}
        for(j = 0; j < pax[0].length; j++){pax[iend][j] = displpax[j];}
        for(j = 0; j < pax[0].length; j++){pax[m][j] = comppax[j];}
	alpaxselected.add(ii);
      }
    }
  }

  private void  tripleGroup(int inum, int[][]pax, int totalpax){
   tripleGroup(new ArrayList(),inum, pax, totalpax);
  }

  /**
   * given the number of pax needed to form groups of three
   * select indivduals from the present population
   * to rearrange their order so they'll board the aircraft together
   * @param alpaxselected
   * @param inum the number of  pax needed to form groups
   * @param pax the array of ordered passengers
   * @param totalpax the total number of passengers
   */
  private void  tripleGroup(ArrayList alpaxselected, int inum, int[][]pax, int totalpax){
    int[] tmppax = new int[NUM_PROPERTIES];
    Integer ii = null;
    Integer kk = null;
    int j = 0;
    int k = 0;
    int m = 0;
    int soughtseat = 0;
    int i = -1;
    boolean found = false;

    for(int n = 1; n <= inum/3; n++){
      soughtseat = 0;
      k = (int)(random.nextDouble()*(double)totalpax);
      kk = new Integer(k);
      while(alpaxselected.contains(kk) && alpaxselected.size() < totalpax){
	k =(int)(random.nextDouble()*(double)totalpax); 
	kk = new Integer(k);
      }
      alpaxselected.add(kk);
      m = k-1;
      soughtseat = pax[k][ineededseat]-1;
      if(soughtseat < 0) {
        soughtseat = 1;
        m = k+1;
      }
      i = -1;
      found = false;
      while(i < totalpax && !found){
        i++;
	ii = new Integer(i);
        if(i != k  && !alpaxselected.contains(ii)) {
	  if(pax[i][ineededseat] == soughtseat) found = true;
        }
      }
      if(found) {
        for(j = 0; j <pax[i].length; j++){tmppax[j] = pax[i][j];}
        for(j = 0; j <pax[k].length; j++){pax[i][j] = pax[m][j];}
        for(j = 0; j < pax[0].length; j++){pax[m][j] = tmppax[j];}
	alpaxselected.add(ii);
      }
    }
  }

/**
 * Get the SeatingFlag value.
 * @return the SeatingFlag value.
 */
public int getSeatingFlag() {
  return iseatingflag;
}

/**
 * Set the SeatingFlag value.
 * @param newSeatingFlag The new SeatingFlag value.
 */
public void setSeatingFlag(int newSeatingFlag) {
  this.iseatingflag = newSeatingFlag;
}


/**
 * Get the ThruPaxCount value.
 * @return the ThruPaxCount value.
 */
public int getThruPaxCount() {
  return itpax;
}

/**
 * Set the ThruPaxCount value.
 * @param newThruPaxCount The new ThruPaxCount value.
 */
public void setThruPaxCount(int newThruPaxCount) {
  this.itpax = newThruPaxCount;
}


/**
 * Get the BoardingPaxCount value.
 * @return the BoardingPaxCount value.
 */
public int getBoardingPaxCount() {
  return boardingpax;
}

/**
 * Set the BoardingPaxCount value.
 * @param newBoardingPaxCount The new BoardingPaxCount value.
 */
public void setBoardingPaxCount(int newBoardingPaxCount) {
  this.boardingpax = newBoardingPaxCount;
}



}// ACGridSim
