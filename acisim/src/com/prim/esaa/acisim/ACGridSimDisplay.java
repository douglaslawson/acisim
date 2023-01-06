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

    public class ACGridSimDisplay extends SimDisplay implements ActionListener  {
    private static SimDisplayParams params = null;
    private static ACGridSimDisplay sd = null;

    public ACGridSimDisplay ()
    {
    }

    public static void main ( String [] args ) 
    {
        params = new SimDisplayParams(0.0, false, SimParams.FIXED, 0.05, false, false );
 	System.out.println("Modeling type " + params.getModelingType());
        sd = new ACGridSimDisplay();
	sd.setSimDisplayParams(params);
	try {
		sd.initialize();
	} catch (SQLException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	sd.setDisplayTitle("Aircraft Interior");
	sd.show();
    }

    public void actionPerformed ( ActionEvent e ) 
    {
      super.actionPerformed(e);
    }
}
