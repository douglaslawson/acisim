package com.prim.esaa.acisim;

import java.io.*;
import java.lang.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.*;
import java.lang.reflect.*;
import com.prim.util.*;
import com.prim.data.*;
import com.prim.esaa.travel.FlightLeg;
import com.prim.esaa.travel.Passenger;
import com.prim.gp.RandomConstant;
import com.prim.esaa.*;
import com.prim.esaa.aci.ACISpatialModeler;
import com.prim.esaa.aci.ACSeat;
import com.prim.esaa.aci.JetBridgeLoader;
import com.prim.esaa.gui.SimDisplay;
import com.prim.esaa.gui.SimDisplayFrameGrabber;
import com.prim.esaa.gui.SimDisplayParams;
import com.prim.esaa.rts.*;
import com.prim.esaa.sta.*;

/**
 * The Aircraft Interior Simulator takes in the passenger data, instantiates 
 * the ACISpatialModeler, instantiates the passengers, a couple of  CMOInserters, a ThreadStop, and
 * a SimClock.
 * It sets itself up as an observer of all the seats and starts the SimClock
 * the update method which is used by the seats stops the simclock and either
 * sets up the tasks for the passengers, gives them to the inserters and starts the simclock again 
 * 
 *
 * @author     Doug Lawson
 * @version    %I%, %G%
 *
 */

public class ACISimulator  extends Simulator implements Observer{
    // Static variables of default values
    private static final int   MAXSIMNUM        = 500;
    private static ACISpatialModeler acispatialmodeler = null;
    private static ACISimDisplay display = null;
    private SimDisplayFrameGrabber framegrabber;
    private int natureofevolution = SimParams.TASK_EVOLUTION;
    private boolean cmochangemind = false;
    private boolean useaveragedtasks = true;
    private boolean found = false;
    private static int maxsimnum = MAXSIMNUM;
    private static int simcount = 0;
    private int totalpaxsought = 0;
    private int newtotalpaxsought = 0;
    private int totalpaxextracted = 0;
    private double maxtime = -1.0e32;
    private double mintime = 1.0e32;
    private static ACISimDisplayParams acidp = null;
    private ThreadStop threadstop = new ThreadStop();
    private SimClock simclock;
    private Vector vpax = new Vector();
    private Vector vseats = new Vector();
    private Vector voccuppiedseats = new Vector();
    private Corridor insertionobject = null;
    private ACSeat acseat = new ACSeat();
    private static String strstartingdate = null;
    private SimpleDateFormat csdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
    private SimpleDateFormat csdf1 = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
    private SimpleDateFormat csdf2 = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
    private SimpleDateFormat csdf3 = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
    private SimpleDateFormat csdf4 = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
    private SimpleDateFormat csdf5 = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
    Random rg = new Random();
	private static CMOExtracter cmox = null;
	private Vector <CognitiveMovingObject>vtmpmobjs = new Vector();
	private int maxpax;
	private JetBridgeLoader jbl;
	private static CognitiveMovingObject monitoredcmo = null;

    public ACISimulator() {
   }

    public void initialize ( SimDisplayParams sdp, SimDisplay disp ) {

      //	Logger logger = new Logger("/opt/fin/lawson/work/acisim/simaci.properties" );
	//       	logger.redirectOut();
	acidp = (ACISimDisplayParams) sdp;
	if(acidp == null) {System.out.println("STASimParams is null"); return;}
	//
	// get parameters from GUI
	//
	disp.addObserver(this);
	natureofevolution = acidp.getNatureofEvolution();
	maxsimnum = (int)acidp.getNumberofSimulations( ); 
	totalpaxsought = acidp.getPassengerCount( );  
	maxpax = acidp.getPassengerMaximumCount();
	newtotalpaxsought = totalpaxsought;

    //
    // instantiate spatial model
    //

	try {
	acispatialmodeler = (ACISpatialModeler)Class.forName(acidp.getSpatialModel()).newInstance();
	} catch(ClassNotFoundException cnfe) {cnfe.printStackTrace(); return;}
	  catch (IllegalAccessException iae) { iae.printStackTrace(); return;}
	  catch (InstantiationException ie) { ie.printStackTrace(); return;}

	//
    // lay out the structure of the cellular model
   	//
	acispatialmodeler.buildModel();
	//
	// initialize stationary objects
	//
	Vector vtsobjs = acispatialmodeler.getModel();
	Corridor tmpcor = null;
	for (int i = 0; i < vtsobjs.size(); i++) {
	  tmpcor = (Corridor)vtsobjs.elementAt(i);
	  tmpcor.setStepResolution(true);
	  tmpcor.addObserver(disp);
	  //	  disp.update(tmpcor,tmpcor);
	}
	//
	//set up the insertion of arriving passengers into the insertion object
	//
	insertionobject = (Corridor)acispatialmodeler.getInsertionObjects().firstElement();
	//
	// set the boarding passengers
	//
	setBoardingPassengers();

	//
	// setup the extraction
	//
	vseats = acispatialmodeler.getSeats();
//	for (int i = 0; i < vseats.size(); i++) {
//		System.out.println("ACISimulator seats " + ((ACSeat)vseats.elementAt(i)).getName());
//		((ACSeat)vseats.elementAt(i)).addObserver(this);
//	}
	//
	// set up the simulation clock 
	// give the inserter and extracters the simclock 
	//
	// instantiate the common thread stopper and 
	//
	// start the simclock
	//

	int minutes = 0;
	strstartingdate = new String(acidp.getSchedDate() + " 00:00:00");
	try {
	    csdf.parse(strstartingdate);
	} catch (ParseException pe) {pe.printStackTrace();}
	Calendar cal = csdf.getCalendar();
	cal.add(Calendar.MINUTE, minutes);
	long seconds = cal.getTime().getTime()/1000;
	strstartingdate = csdf.format(new Date(seconds*1000));
        System.out.println("ACISimulator initialize " + strstartingdate );
	threadstop.setStop(false);
	simclock = new SimClock(threadstop, strstartingdate);
	simclock.setSleep(acidp.getClockSpeed());	
	simclock.addSimClockListener(insertionobject);
	insertionobject.initialize();

	Vector vsobj = acispatialmodeler.getModel();
	int[] index1 = new int[0];
	for(int i = 0; i < vsobj.size(); i++) {
			simclock.addSimClockListener((Conduit) vsobj.elementAt(i));
			((Corridor) vsobj.elementAt(i)).initialize ();
	}
	
	//
	// setup the extraction
	//
	vseats = acispatialmodeler.getSeats();
    cmox = new CMOExtracter(vseats,
    		new Passenger(),
    		false,
    		totalpaxsought
    		);
    cmox.addObserver(this);
	if(null != cmox) simclock.addSimClockListener(cmox);

	//
	// create the inserter
	//
	jbl = new JetBridgeLoader(insertionobject, vtmpmobjs, (long)15);
	simclock.addSimClockListener(jbl);
	//
	// framegrabber must be last clock listener to get latest image
	//
	if(acidp.makeMovie()) {
	  framegrabber = new SimDisplayFrameGrabber (disp.getImage(),new Integer(simcount).toString().trim(),acidp.getMovieFormat(),acidp.getSaveMoviePath());
	simclock.addSimClockListener(framegrabber);
	  
	}
	//
	// start the simuation
	//
       	Thread thread = new Thread(simclock);
       	thread.start();
    }

    
    private void setBoardingPassengers() {
		double avoiddistance = 36.0;
      	if(acidp.getBoardingPattern() == acidp.DWILMA) {vtmpmobjs = acispatialmodeler.generateWilmaPassengers(totalpaxsought);}
       	if(acidp.getBoardingPattern() == acidp.OPEN) {vtmpmobjs = acispatialmodeler.generateOpenSeatingPassengers(totalpaxsought, maxpax);}
    	for (int i = 0; i < vtmpmobjs.size(); i++) {
    		CognitiveMovingObject tcmobj = (CognitiveMovingObject)vtmpmobjs.elementAt(i);
     		if(acidp.getModelingType() == acidp.FIXED) tcmobj.setCanChangeMind(false);
     		tcmobj.c.cctr.x = insertionobject.getLength() - ((i+1)*(tcmobj.getLength()+tcmobj.c.cdx*2.0));
     		tcmobj.getCharacteristics().setInitialX(tcmobj.getCharacteristics().getCurrentX());
			avoiddistance = tcmobj.getCharacteristics().getCurrentAvoidanceSteps().doubleValue()*Math.sqrt((tcmobj.idxmax*tcmobj.idxmax) + (tcmobj.idymax*tcmobj.idymax));
			tcmobj.setCurrentAvoidanceDistance( new Double((avoiddistance > (tcmobj.iradiusmin*10.0) ? avoiddistance : (tcmobj.iradiusmin*10.0))));
			tcmobj.setCurrentAvoidanceSteps(new Double(20.0));
    		System.out.println ("ACISimulator " + 
    			      tcmobj.getName() + " " + 
    			      tcmobj.getGoal().getName() + " " + 
    			      tcmobj.getGoal().getCurrentX(insertionobject) + " " + 
    			      tcmobj.getGoal().getCurrentY(insertionobject) + " " + 
    			      tcmobj.c.cwidth + " " + 
    			      tcmobj.c.clength + " " + 
    			      tcmobj.getCurrentWidth() + " " + 
    			      tcmobj.getCurrentLength() + " " + 
    			      tcmobj.c.cdx + " " + 
    			      tcmobj.c.cdy + " " + 
    			      tcmobj.c.cctr.x + " " + 
    			      tcmobj.c.cctr.y + " " + 
    			      tcmobj.c.getInitialX() + " " + 
    			      tcmobj.c.getInitialY());
              Vector vtasks = tcmobj.getTasks();
              for (int j = 0; j < vtasks.size(); j++ ) {
                Task task = (Task)vtasks.elementAt(j);
                Vector vactivities = task.getActivities();
                 Activity activity = (Activity)vactivities.elementAt(0);
                  Characteristics chars  = activity.getCharacteristics();
    	      chars.cctr.x = insertionobject.getLength() - ((i+1)*tcmobj.getCurrentLength().doubleValue()*3.0);
    	      chars.setInitialX(chars.getCurrentX());
    	      chars.setCurrentLength(tcmobj.getCurrentLength());
    	      chars.setCurrentWidth(tcmobj.getCurrentWidth());
                for (int k = 0; k < vactivities.size(); k++ ) {
                  activity = (Activity)vactivities.elementAt(k);
                  Goal goal  = activity.getGoal();
    	      Vector vportals = goal.getOpening().getPortals();
                  for (int m = 0; m < vportals.size(); m++ ) {
    	        Portal portal =  (Portal)vportals.elementAt(m);
    	        System.out.println("ACISimulator " + 
    			       "pax " + i+ " " + 
    			       "task " + j+ " " + 
    			       "activity " + k+ " " + 
    			       "goal opening portal " + m+ " " + 
    			       portal.getStationaryObject().getName() + " " + 
    			       portal.getStationaryObject().getLength() + " " + 
    			       portal.getStationaryObject().getWidth() + " " + 
     			       portal.getCenterX() + " " + 
      			       portal.getCenterY() + " " +
    			       goal.getCurrentX(portal.getStationaryObject()) + " " +
    			       goal.getCurrentY(portal.getStationaryObject())
    			       );
    	      }
                }
              }
    	}		
	}

	/**
     * Upon Completion of a Simulation
     * stop the simclock
     * start the simclock
     */

    public void update(Observable o, Object  arg) {
    	Vector vactivities;
    	SimMessage simmessage;
    	double entrywaytime = 0.0;
    	double seattime = 0.0;
    	if( arg instanceof SimDisplayParams) {	
    		System.out.println("ACISimulator update SimDisplayParams ");
    		acidp = (ACISimDisplayParams) arg;
    		newtotalpaxsought = acidp.getPassengerCount();
    	}else if (arg instanceof SimMessage) {
    		System.out.println("ACISimulator update SimMessage ");
    		simmessage = (SimMessage) arg;
    		if(simmessage.type.equals("All Sought Extracted")) {
    			//
    			// give the framegrabber a SimClock Event
    			//
    			if(framegrabber != null) framegrabber.tick(new SimClockEvent(simclock,simclock));
    			//
    			// stop threads
    			//
    			threadstop.setStop(true);
   				//
				// increment the simulations counter
				//
  				simcount++;
  				//
    			// if there are more simulations to do
    			//
   				if(simcount < maxsimnum) {
    				System.out.println("CounterFlowSimulator update simcount " + simcount);
    				//
    				// display properties of monitored CognitiveMovingObjects
    				//
    				if(monitoredcmo != null) {
    					monitoredcmo.setChanged();
    					monitoredcmo.notifyObservers(monitoredcmo);
    					System.out.println("CounterFlowSimulator: Monitored CMO notified observer.");
    				}
    				if(acidp.getModelingType() == acidp.EVOLVING)	{
    					// TODO if evolving store the memories and recollect
    				}else {
    					//
    					// reset inserter
    					//
    					setBoardingPassengers();
    					jbl.setCMOs(vtmpmobjs);
    				}
    		        totalpaxextracted = 0;
    		        //
    				// Since the occupants of seats are also in the ACRow
    		        // they must now be removed from the ACRows
    				//
    		        int index;
    		        Vector<CognitiveMovingObject>vcmos = cmox.getCMOsExtracted();
    		        Vector<Portal>vportals;
    		        for(CognitiveMovingObject cmo: vcmos) {
    		        	vportals =  cmo.getGoal().getOpening().getPortals();
    		        	index = (vportals.get(0).getStationaryObject() instanceof ACSeat ? 0: 1);
    		        	Corridor sobj = (Corridor) cmo.getGoal().getOpening().getOtherPortal(vportals.get(index).getStationaryObject() ).getStationaryObject();
    		        	sobj.extractMovingObject(cmo);
    		        }
      		        //
    		        // reset the extractor
    		        //
    		        cmox.initialize(vseats, new Passenger(), (simcount < maxsimnum-1 ? false: true), newtotalpaxsought);
    				//
    				// reset and restart clock
    				//
    				simclock.setDate(strstartingdate);
    				//
    				// restart threads
    				//
    				threadstop.setStop(false);
    			} else {
    				//
    				// finished all simulations
    				// save all CMOs for evolutionary analysis
    			}
    		}
    	} else if (arg instanceof ACSeat ) {
    		System.out.println("ACISimulator update ACSeat");
    		ACSeat seat = (ACSeat)arg;
    		if(seat.getOccupant() != null) {
    			System.out.println("ACISimulator update " + seat.getName() + " " +
    					voccuppiedseats.size() + " " +
    					vseats.size() + " " +
    					totalpaxextracted + " " + 
    					totalpaxsought
    					);
    			found = false;
    			int i = 0;
    			while(!found && i < voccuppiedseats.size()) {
    				if(((ACSeat)voccuppiedseats.elementAt(i)).equals(seat))found = true;
    				i++;
    			}
    			if(!found) {
    				vactivities  =((Task)seat.getOccupant().getTasks().firstElement()).getActivities();
    				entrywaytime = ((Activity)vactivities.elementAt(0)).getCharacteristics().getCurrentTime().doubleValue();
    				seattime = ((Activity)vactivities.elementAt(2)).getCharacteristics().getCurrentTime().doubleValue();
    				System.out.println("ACISimulator update " + seat.getName() + " " + entrywaytime+ " " + seattime);
    				if(entrywaytime  < mintime) mintime = entrywaytime;
    				if(seattime > maxtime) maxtime = seattime;		
    			}
    		} else {
    		}
        }
    }
    
    /**
     * Upon Completion of a Simulation
     * stop the simclock
     * start the simclock
     */

    public void updateold(Observable o, Object  arg) {
      Vector vactivities;
	SimMessage simmessage;
	double entrywaytime = 0.0;
	double seattime = 0.0;
	if( arg instanceof SimDisplayParams) {
		System.out.println("ACISimulator update SimDisplayParams ");
		acidp = (ACISimDisplayParams) arg;
		newtotalpaxsought = acidp.getPassengerCount();
		// create vector of passengers and put them in the corridor
	}else if (arg instanceof SimMessage) {
		System.out.println("ACISimulator update SimMessage ");
	    simmessage = (SimMessage) arg;
	    if(simmessage.type.equals("All Sought Extracted")) {
			//
			// give the framegrabber a SimClock Event
			//
			if(framegrabber != null) framegrabber.tick(new SimClockEvent(simclock,simclock));
			//
			// stop threads
			//
			threadstop.setStop(true);
			//
			// if there are more simulations to do
			//
			if(simcount < maxsimnum) {
				System.out.println("CounterFlowSimulator update simcount " + simcount);
			//
			// display properties of monitored CognitiveMovingObjects
			//
			if(monitoredcmo != null) {
				monitoredcmo.setChanged();
				monitoredcmo.notifyObservers(monitoredcmo);
				System.out.println("CounterFlowSimulator: Monitored CMO notified observer.");
			}
			//
			// clear the extractor
			//
			cmox.initialize(vseats, new Passenger(), false, newtotalpaxsought);
			//
			// reset and restart clock
			//
			simclock.setDate(strstartingdate);
			//
			// restart threads
			//
			threadstop.setStop(false);
			//
			// increment the simulations counter
			//
			simcount++;
			} else {
				//
				// finished all simulations
				// save all CMOs for evolutionary analysis
			}
          }
	} else if (arg instanceof ACSeat ) {
	    System.out.println("ACISimulator update ACSeat");
	    ACSeat seat = (ACSeat)arg;
	    if(seat.getOccupant() != null) {
	      System.out.println("ACISimulator update " + seat.getName() + " " +
			       voccuppiedseats.size() + " " +
			       vseats.size() + " " +
                               totalpaxextracted + " " + 
			       totalpaxsought
			       );
	      found = false;
	      int i = 0;
	      while(!found && i < voccuppiedseats.size()) {
	        if(((ACSeat)voccuppiedseats.elementAt(i)).equals(seat))found = true;
		i++;
	      }
	      if(!found) {
		vactivities  =((Task)seat.getOccupant().getTasks().firstElement()).getActivities();
		entrywaytime = ((Activity)vactivities.elementAt(0)).getCharacteristics().getCurrentTime().doubleValue();
		seattime = ((Activity)vactivities.elementAt(2)).getCharacteristics().getCurrentTime().doubleValue();
	        System.out.println("ACISimulator update " + seat.getName() + " " + entrywaytime+ " " + seattime);
	        if(entrywaytime  < mintime) mintime = entrywaytime;
	        if(seattime > maxtime) maxtime = seattime;		
	        voccuppiedseats.add(seat);
//	        totalpaxextracted++;
	      }
              if(totalpaxextracted == totalpaxsought) {
		    if(framegrabber != null) framegrabber.tick(new SimClockEvent(simclock,simclock));
		    threadstop.setStop(true);
	            System.out.println("ACISimulator update simcount " + simcount+ " " + mintime+ " " + maxtime  + " " + totalpaxsought + " " + (maxtime-mintime));
		    //
		    // increment the simulations counter
		    //
	            simcount++;
		    if(simcount < maxsimnum) {
		        //
		        // clear the occuppied seats
		    	// and ACRow of moving objects
		        //
		        totalpaxsought = newtotalpaxsought;
		        for (i = 0; i < voccuppiedseats.size(); i++) {
		        	seat = (ACSeat)voccuppiedseats.elementAt(i);
		        	seat.extractAllMovingObjects();
		        	Vector <Opening>vopenings = seat.getOpenings();
		        	for(Opening opening :vopenings) {((Corridor)opening.getOtherPortal(seat).getStationaryObject()).extractAllMovingObjects();;}		  
		        }
		        //
		        // reset insertor
		        //
		        jbl.setCMOs(vtmpmobjs);
		        totalpaxextracted = 0;
				//
				// clear the extractor
				//
				cmox.initialize(vseats, new Passenger(), (simcount < maxsimnum -1 ? false: true), totalpaxsought);
		        //
		        // reset and restart clock
		        //
		        simclock.setDate(strstartingdate);
		        threadstop.setStop(false);
		    }
	      }
	    } else {
	      found = false;
	      int i = 0;
	      int index = 0;
	      while(!found && i < voccuppiedseats.size()) {
	        if(((ACSeat)voccuppiedseats.elementAt(i)).equals(seat)){ found = true; index = i;}
		i++;
	      }
	      if(found) {
	        System.out.println("ACISimulator update " + seat.getName() + " now empty ");
	        voccuppiedseats.removeElementAt(index);
	        totalpaxextracted--;
	      }
	    }
        }
    }


    private boolean updateLobby(Vector vcmobjs){
        return true;
    }

     /**
     * @param Schedule Date yyyyMMDD
     * @param minutes into the day
     * @return the number of seconds since the beginning of time
     */
    private long convertMIDtoSimTime(String schedDate, int minutes){
	long seconds = minutes*60;
	    try {
	         csdf.parse(schedDate + " 00:00:00");
	    } catch (ParseException pe) {pe.printStackTrace();}
	    Calendar cal = csdf.getCalendar();
	    long scheddate =  cal.getTime().getTime()/1000;
	    return (seconds + scheddate);
	
    }

  private void sortByRow(Vector v) {
      Vector vparams = new Vector();
      try {
	JPrimQSortParams gettaskparams = new JPrimQSortParams(Class.forName("com.prim.esaa.CognitiveMovingObject"),"getTasks",null, null);
	vparams.add(gettaskparams);

	JPrimQSortParams firsttaskparams = new JPrimQSortParams(Class.forName("java.util.Vector"),"firstElement",null, null);
	vparams.add(firsttaskparams);
	
	JPrimQSortParams getactparams = new JPrimQSortParams(Class.forName("com.prim.esaa.Task"),"getActivities",null, null);
	vparams.add(getactparams);

	JPrimQSortParams firstactparams = new JPrimQSortParams(Class.forName("java.util.Vector"),"lastElement",null, null);
	vparams.add(firstactparams);

	JPrimQSortParams getgoalparams = new JPrimQSortParams(Class.forName("com.prim.esaa.Activity"),"getGoal",null, null);
	vparams.add(getgoalparams);

	JPrimQSortParams getsobjparams = new JPrimQSortParams(Class.forName("com.prim.esaa.Goal"),"getContainer",null, null);
	vparams.add(getsobjparams);
	JPrimQSortParams getnameparams = new JPrimQSortParams(Class.forName("com.prim.esaa.Conduit"),"getName",null, null);
	vparams.add(getnameparams);

	JPrimQSort sorter = new JPrimQSort();
	  sorter.initialize(vparams, JPrimQSort.DESCENDING);
	  sorter.Sort(v, v.size());
      } catch(NoSuchMethodException nsme) { nsme.printStackTrace();}
      catch(ClassNotFoundException cnfe) { cnfe.printStackTrace();}
  }

}
