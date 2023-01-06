package com.prim.esaa.acisim;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.border.*;
import javax.swing.colorchooser.*;
import javax.swing.filechooser.*;
import javax.accessibility.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.beans.*;
import java.util.*;
import java.io.*;
import java.applet.*;
import java.net.*;
import java.sql.SQLException;

import com.prim.ui.*;
import com.prim.esaa.*;
import com.prim.esaa.gui.SimDisplay;
import com.prim.esaa.gui.SimDisplayParams;
import com.prim.esaa.rts.*;
import com.prim.esaa.sta.STASimDisplayPanel;
import com.prim.esaa.sta.STASimParams;
import com.prim.data.*;

    public class ACISimDisplay extends SimDisplay implements ActionListener  {
    private static ACISimDisplayParams params = null;
    private static ACISimDisplay sd = null;
	private static JMenuItem miOpen;
	private static JMenuItem miDWilMA;

    public ACISimDisplay ()
    {
    }

    public static void main ( String [] args ) {
        params = new ACISimDisplayParams(0.0, false, SimParams.EVOLVING, 0.05, false, false );
        System.out.println("Modeling type " + params.getModelingType());
        sd = new ACISimDisplay();
        sd.setSimDisplayParams(params);
        try {
			sd.initialize("com.prim.esaa.aci.ACISpatialModeler",
			 		   "com.prim.esaa.gui.SimDisplayParams",
			 		   "com.prim.esaa.acisim.ACISimulator");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	   
        sd.setDisplayTitle("Aircraft Interior");
        sd.getMenuBar().add(createBoardingMenu(sd));
         sd.show();
    }

public static JMenuItem createBoardingMenu(ACISimDisplay sd) {
	   JMenuItem mBoarding =  new JMenu("Boarding");
	   miOpen  = new JMenuItem("Open Seating");
	   miOpen.setFont(new Font("TimesRoman",Font.BOLD,12));
	   miOpen.addActionListener ( sd );
	   mBoarding.add(miOpen);

	   miDWilMA = new JMenuItem("D-WilMA");
	   miDWilMA.setFont(new Font("TimesRoman",Font.BOLD,12));
	   miDWilMA.addActionListener ( sd );
	   mBoarding.add(miDWilMA);
	   mBoarding.add(new JSeparator());
   
      return mBoarding;
   }
    public void actionPerformed ( ActionEvent e ) {
    	if(params.getPassengerCount() < 1)params.setPassengerCount(1); 
    	if (e.getSource () == miOpen) {
 		   this.setOpenSeatingPax(137);	
 	   }else if (e.getSource () == miDWilMA) {
 		   this.setDWilMAPax(137);
 	   }
 	   super.actionPerformed(e);
    }

	private void setDWilMAPax(int maxpax) {
		   params.setBoardingPattern(params.DWILMA);	
		   setPassengerCounts(maxpax);
	}

	private void setPassengerCounts( int maxpax) {
		params.setPassengerMaximumCount(maxpax);
	}

	private void setOpenSeatingPax(int maxpax) {
		   params.setBoardingPattern(params.OPEN);	
		   this.setPassengerCounts( maxpax);	
	}
}
