package routing.community;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.DTNHost;
import core.Settings;
import core.SimClock;

public class TemporalCentrality implements Centrality
{
	/** length of time to consider in each epoch -setting id {@value} */
	public static final String CENTRALITY_WINDOW_SETTING = "timeWindow";
	/** time interval between successive updates to centrality values -setting id 
	 * 		{@value} */
	public static final String COMPUTATION_INTERVAL_SETTING = "computeInterval";
	/** Number of time windows over which to average -setting id {@value} */
	public static final String EPOCH_COUNT_SETTING = "nrOfEpochsToAvg";
	
	/** Time to wait before recomputing centrality values (node degree) */
	protected static int COMPUTE_INTERVAL = 600; // seconds, i.e. 10 minutes
	/** Width of each time interval in which to count the node's degree */
	protected static int CENTRALITY_TIME_WINDOW = 21600; // 6 hours
	/** Number of time intervals to average the node's degree over */
	protected static int EPOCH_COUNT = 5;
	
	/** Saved global centrality from last computation */
	protected double globalCentrality;
	/** Saved local centrality from last computation */
	protected double localCentrality;
	
	/** timestamp of last global centrality computation */
	protected int lastGlobalComputationTime;
	/** timestamp of last local centrality computation */ 
	protected int lastLocalComputationTime;
	
	public TemporalCentrality(Settings s) 
	{
		if(s.contains(CENTRALITY_WINDOW_SETTING))
			CENTRALITY_TIME_WINDOW = s.getInt(CENTRALITY_WINDOW_SETTING);
		
		if(s.contains(COMPUTATION_INTERVAL_SETTING))
			COMPUTE_INTERVAL = s.getInt(COMPUTATION_INTERVAL_SETTING);
		
		if(s.contains(EPOCH_COUNT_SETTING))
			EPOCH_COUNT = s.getInt(EPOCH_COUNT_SETTING);
	}
	
	public TemporalCentrality(TemporalCentrality proto)
	{
		// set these back in time (negative values) to do one computation at the 
		// start of the sim
		this.lastGlobalComputationTime = this.lastLocalComputationTime = 
			-COMPUTE_INTERVAL;
	}
	
	public double getGlobalCentrality(Map<DTNHost, List<Duration>> connHistory)
	{
	
		if(SimClock.getIntTime() - this.lastGlobalComputationTime < COMPUTE_INTERVAL)
			return globalCentrality;
		
		// initialize
		int[] centralities = new int[EPOCH_COUNT];
		int epoch, timeNow = SimClock.getIntTime();
		Map<Integer, Set<DTNHost>> nodesCountedInEpoch = 
			new HashMap<Integer, Set<DTNHost>>();
		
		for(int i = 0; i < EPOCH_COUNT; i++)
			nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
		
		/*
		 * For each node, loop through connection history until we crossed all
		 * the epochs we need to cover
		 */
		for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
		{
			DTNHost h = entry.getKey();
			for(Duration d : entry.getValue())
			{
			//try{
				int timePassed = (int)(timeNow - d.end);
				
				// if we reached the end of the last epoch, we're done with this node
				if(timePassed > CENTRALITY_TIME_WINDOW * EPOCH_COUNT)
					break;
				
				// compute the epoch this contact belongs to
				epoch = timePassed / CENTRALITY_TIME_WINDOW;
				
				if (epoch >= EPOCH_COUNT) 
					continue;
				
				// Only consider each node once per epoch
				Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(epoch);
				if(nodesAlreadyCounted.contains(h))
					continue;
				
				// increment the degree for the given epoch
				centralities[epoch]++;
				nodesAlreadyCounted.add(h);
				//}catch(NullPointerException e){}
			}
		}
		
		// compute and return average node degree
		int sum = 0;
		for(int i = 0; i < EPOCH_COUNT; i++) 
			sum += centralities[i];
		this.globalCentrality = ((double)sum) / EPOCH_COUNT;
		
		this.lastGlobalComputationTime = SimClock.getIntTime();
		
		return this.globalCentrality;
		
	}

	public double getLocalCentrality(Map<DTNHost, List<Duration>> connHistory,
			CommunityDetection cd)
	{
	
		if(SimClock.getIntTime() - this.lastLocalComputationTime < COMPUTE_INTERVAL)
			return localCentrality;
		
		// centralities will hold the count of unique encounters in each epoch
		int[] centralities = new int[EPOCH_COUNT];
		int epoch, timeNow = SimClock.getIntTime();
		Map<Integer, Set<DTNHost>> nodesCountedInEpoch = 
			new HashMap<Integer, Set<DTNHost>>();
		
		for(int i = 0; i < EPOCH_COUNT; i++)
			nodesCountedInEpoch.put(i, new HashSet<DTNHost>());
		
		// local centrality only considers nodes in the local community
		Set<DTNHost> community = cd.getLocalCommunity();
		
		/*
		 * For each node, loop through connection history until we crossed all
		 * the epochs we need to cover
		 */
		for(Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet())
		{
			DTNHost h = entry.getKey();
			
			// if the host isn't in the local community, we don't consider it
			if(!community.contains(h))
				continue;
			
			for(Duration d : entry.getValue())
			{
			//try{
				int timePassed = (int)(timeNow - d.end);
				
				// if we reached the end of the last epoch, we're done with this node
				if(timePassed > CENTRALITY_TIME_WINDOW * EPOCH_COUNT)
					break;
				
				// compute the epoch this contact belongs to
				epoch = timePassed / CENTRALITY_TIME_WINDOW;
				
				if (epoch >= EPOCH_COUNT) 
					continue;
				
				// Only consider each node once per epoch
				Set<DTNHost> nodesAlreadyCounted = nodesCountedInEpoch.get(epoch);
				if(nodesAlreadyCounted.contains(h))
					continue;
				
				// increment the degree for the given epoch
				centralities[epoch]++;
				nodesAlreadyCounted.add(h);
				//}catch(NullPointerException e){}
			}
		}
		
		// compute and return average node degree
		int sum = 0;
		for(int i = 0; i < EPOCH_COUNT; i++) 
			sum += centralities[i];
		this.localCentrality = ((double)sum) / EPOCH_COUNT; 
		
		this.lastLocalComputationTime = SimClock.getIntTime();
		
		return this.localCentrality;
		
	}

	public Centrality replicate()
	{
		return new TemporalCentrality(this);
	}

}

