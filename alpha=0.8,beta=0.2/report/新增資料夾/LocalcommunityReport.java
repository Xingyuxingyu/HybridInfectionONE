/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.community.CommunityDetectionEngine;

import core.DTNHost;
import core.Settings;
import core.UpdateListener;


public class LocalcommunityReport extends Report implements UpdateListener {
	/** Reporting granularity -setting id ({@value}). 
	 * Defines the interval how often (seconds) a new snapshot of energy levels
	 * is created */
	public static final String GRANULARITY = "granularity";
	/** Optional reported nodes (comma separated list of network addresses). 
	 * By default all nodes are reported. */
	public static final String REPORTED_NODES = "nodes";
	/** value of the granularity setting */
	protected final int granularity;
	/** time of last update*/
	protected double lastUpdate; 
	/** Networks addresses (integers) of the nodes which are reported */
	protected HashSet<Integer> reportedNodes;
	
	/**
	 * Constructor. Reads the settings and initializes the report module.
	 */
	public LocalcommunityReport() {
		Settings settings = getSettings();
		this.lastUpdate = 0;	
		this.granularity = settings.getInt(GRANULARITY);
		
		if (settings.contains(REPORTED_NODES)) {
			this.reportedNodes = new HashSet<Integer>();
			for (Integer nodeId : settings.getCsvInts(REPORTED_NODES)) {
				this.reportedNodes.add(nodeId);
			}
		}
		else {
			this.reportedNodes = null;
		}
		
		init();
	}

	/**
	 * Creates a new snapshot of the energy levels if "granularity" 
	 * seconds have passed since the last snapshot. 
	 * @param hosts All the hosts in the world
	 */
	public void updated(List<DTNHost> hosts) {
		double simTime = getSimTime();
		if (isWarmup()) {
			return; /* warmup period is on */
		}
		/* creates a snapshot once every granularity seconds */
		if (simTime - lastUpdate >= granularity) {
			printlocalcommunity(hosts);
			this.lastUpdate = simTime - simTime % granularity;
		}
	}
	
	/**
	 * Creates a snapshot of energy levels 
	 * @param hosts The list of hosts in the world
	 */
	/*private void createSnapshot(List<DTNHost> hosts) {
		write ("[" + (int)getSimTime() + "]"); // simulation time stamp 
		for (DTNHost h : hosts) {
			if (this.reportedNodes != null && 
				!this.reportedNodes.contains(h.getAddress())) {
				continue; // node not in the list 
			}
			Double value = (Double)h.getComBus().
				getProperty(routing.EnergyAwareRouter.ENERGY_VALUE_ID);
			if (value == null) {
				throw new SimError("Host " + h + 
						" is not using an energy aware router");
			}
			
			write(h.toString() + " " +  format(value));
		}
	
	}*/
	private void printlocalcommunity(List<DTNHost> hosts) {
		write ("[" + (int)getSimTime() + "]"); // simulation time stamp 
		for (DTNHost h : hosts) {
			
			MessageRouter r = h.getRouter();
			if(!(r instanceof DecisionEngineRouter) )
				continue;
			
			RoutingDecisionEngine de = ((DecisionEngineRouter)r).getDecisionEngine();
			
			if(!(de instanceof CommunityDetectionEngine))
				continue;
			
			CommunityDetectionEngine cd = (CommunityDetectionEngine)de;						
			Set<DTNHost> nodeComm = cd.getLocalCommunity();			
			write( h.toString() + " " + nodeComm.toString());
			
		}
		write("");
	}
	
	
/*	private void printlocalcommunity(List<DTNHost> hosts) {
		write ("[" + (int)getSimTime() + "]"); // simulation time stamp 
		for (DTNHost h : hosts) {
			
			MessageRouter r = h.getRouter();
			if(!(r instanceof DecisionEngineRouter) )
				continue;
			
			RoutingDecisionEngine de = ((DecisionEngineRouter)r).getDecisionEngine();
			
			if(!(de instanceof CommunityDetectionEngine))
				continue;
			
			CommunityDetectionEngine cd = (CommunityDetectionEngine)de;						
			Set<DTNHost> nodeComm = cd.getfamiliarSet();			
			write( h.toString() + " " + nodeComm.toString());
			
		}
		write("");
	}
	*/
	
}
