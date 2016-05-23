/*
 * @(#)DistributedHybridLog.java
 *
 * Copyright 2010 by University of Pittsburgh, released under GPLv3.
 * 
 */
package routing.community;



import java.util.*;

import core.*;
import routing.HybridStrategyRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;


/**
 * <p>Implements the Distributed BubbleRap Routing Algorithm from Hui et al. 
 * 2008 (Bibtex record included for convenience). The paper is a bit fuzzy on 
 * thevactual implementation details. Choices exist for methods of community
 * detection (SIMPLE, K-CLIQUE, MODULARITY) and local centrality approximation
 * (DEGREE, S-WINDOW, C-WINDOW).</p> 
 * 
 * <p>In general, each node maintains an idea of it's local community, a group 
 * of nodes it meets with frequently. It also approximates its centrality within
 * the social network defined by this local community and within the global
 * social network defined by all nodes.</p>
 * 
 * <p>When a node has a message for a destination, D, and D is not part of its 
 * local community, it forwards the message to "more globally central" nodes,
 * those that estimate a higher global centrality value. The intuition here is 
 * that nodes in the center of the social network are more likely to contact the
 * destination. In this fashion the message bubbles up social network to more
 * central nodes until a node is found that reports D in its local community.
 * At this point, the message is only routed with in the nodes of the local 
 * community and propagated towards more locally central nodes or the 
 * destination until delivered.<p>
 * 
 * <pre>
 * \@inproceedings{1374652,
 *	Address = {New York, NY, USA},
 *	Author = {Hui, Pan and Crowcroft, Jon and Yoneki, Eiko},
 *	Booktitle = {MobiHoc '08: Proceedings of the 9th ACM international symposium 
 *		on Mobile ad hoc networking and computing},
 *	Doi = {http://doi.acm.org/10.1145/1374618.1374652},
 *	Isbn = {978-1-60558-073-9},
 *	Location = {Hong Kong, Hong Kong, China},
 *	Pages = {241--250},
 *	Publisher = {ACM},
 *	Title = {BUBBLE Rap: Social-based Forwarding in Delay Tolerant Networks},
 *	Url = {http://portal.acm.org/ft_gateway.cfm?id=1374652&type=pdf&coll=GUIDE&dl=GUIDE&CFID=55195392&CFTOKEN=93998863},
 *	Year = {2008}
 * }
 * </pre>
 * 
 * @author PJ Dillon, University of Pittsburgh
 *
 */
public class DistributedHybridLog 
				implements RoutingDecisionEngine, CommunityDetectionEngine
{
	/** Community Detection Algorithm to employ -setting id {@value} */
	public static final String COMMUNITY_ALG_SETTING = "communityDetectAlg";
	/** Centrality Computation Algorithm to employ -setting id {@value} */
	public static final String CENTRALITY_ALG_SETTING = "centralityAlg";
	
	protected Map<DTNHost, Double> startTimestamps;
	protected Map<DTNHost, List<Duration>> connHistory;
	
	/*new*/
	protected Map<DTNHost, FBStatus> FBfriends;
	protected List<DTNHost> onlymeetme;
	protected int thres_inactive = 2;
	/*new*/
	
	
	protected CommunityDetection community;
	protected Centrality centrality;
	
	public int SignalCost = 0;
	
	
	/**
	 * Constructs a DistributedHybridLog Decision Engine based upon the settings
	 * defined in the Settings object parameter. The class looks for the class
	 * names of the community detection and centrality algorithms that should be
	 * employed used to perform the routing.
	 * 
	 * @param s Settings to configure the object
	 */
	public DistributedHybridLog(Settings s)
	{
		if(s.contains(COMMUNITY_ALG_SETTING))
			this.community = (CommunityDetection) 
				s.createIntializedObject(s.getSetting(COMMUNITY_ALG_SETTING));
		else
			this.community = new SimpleCommunityDetection(s);
		
		if(s.contains(CENTRALITY_ALG_SETTING))
			this.centrality = (Centrality) 
				s.createIntializedObject(s.getSetting(CENTRALITY_ALG_SETTING));
		else
			this.centrality = new SWindowCentrality(s);
	}
	
	/**
	 * Constructs a DistributedHybridLog Decision Engine from the argument 
	 * prototype. 
	 * 
	 * @param proto Prototype DistributedHybridLog upon which to base this object
	 */
	public DistributedHybridLog(DistributedHybridLog proto)
	{
		this.community = proto.community.replicate();
		this.centrality = proto.centrality.replicate();
		startTimestamps = new HashMap<DTNHost, Double>();
		connHistory = new HashMap<DTNHost, List<Duration>>();
		/*new*/
		FBfriends = new HashMap<DTNHost, FBStatus>();
		onlymeetme = new ArrayList<DTNHost>();
		
		
	}

	public void FBread(DTNHost thisHost){
		/*readFBList*/
		
			for(Connection con : thisHost.getInterfaces().get(0).getConnections()){
				FBStatus fri = new FBStatus();
				//FBdegree
				fri.FBdegree = con.getOtherNode(thisHost).getInterfaces().get(0).getConnections().size();
				//System.out.println(thisHost.getAddress()+"	"+con.getOtherNode(thisHost).getAddress());
				FBfriends.put(con.getOtherNode(thisHost),fri);
			}				
		/*readFBList*/
	}
	
	public void connectionUp(DTNHost thisHost, DTNHost peer)
	{	
		
		int threshold = SimClock.getIntTime()/10000*thres_inactive;
		/*new*/
		if(FBfriends.containsKey(peer)){
			//FB number of contact
			SignalCost++;
			FBfriends.get(peer).Nrofcontact = FBfriends.get(peer).Nrofcontact+1;
		}
		if(!this.onlymeetme.contains(peer)){
			if(getOtherDecisionEngine(peer).FBfriends.isEmpty()){		
				this.onlymeetme.add(peer);
			}
			else if(getNodeInTime(getOtherDecisionEngine(peer).connHistory)<=threshold){
				this.onlymeetme.add(peer);
			}
		}
		else if(getNodeInTime(getOtherDecisionEngine(peer).connHistory)>threshold){
			SignalCost++;
			if(!getOtherDecisionEngine(peer).FBfriends.isEmpty())
			this.onlymeetme.remove(peer);
		}
		else{
			SignalCost++;
		}
		/*new*/
		
	
	}

	/**
	 * Starts timing the duration of this new connection and informs the community
	 * detection object that a new connection was formed.
	 * 
	 * @see routing.RoutingDecisionEngine#doExchangeForNewConnection(core.Connection, core.DTNHost)
	 */
	public int doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		DTNHost myHost = con.getOtherNode(peer);
		DistributedHybridLog de = this.getOtherDecisionEngine(peer);
		
		this.startTimestamps.put(peer, SimClock.getTime());
		de.startTimestamps.put(myHost, SimClock.getTime());
		
		return this.community.newConnection(myHost, peer, de.community);
	}
	
	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
		double time = startTimestamps.get(peer);
		double etime = SimClock.getTime();
		int newCost = 0;
		
		// Find or create the connection history list
		List<Duration> history;
		if(!connHistory.containsKey(peer))
		{
			history = new LinkedList<Duration>();
			connHistory.put(peer, history);
			newCost++;
		}
		else
			history = connHistory.get(peer);
		
		// add this connection to the list
		if(etime - time > 0){
			history.add(new Duration(time, etime));
		 	newCost = newCost + 2 ;
		}
		CommunityDetection peerCD = this.getOtherDecisionEngine(peer).community;
		
		// inform the community detection object that a connection was lost.
		// The object might need the whole connection history at this point.
		community.connectionLost(thisHost, peer, peerCD, history);
		
		startTimestamps.remove(peer);
		((HybridStrategyRouter)thisHost.getRouter()).addSignalCost(newCost*8);
	}

	public boolean newMessage(Message m)
	{
		return true; // Always keep and attempt to forward a created message
	}

	public boolean isFinalDest(Message m, DTNHost aHost)
	{
		return m.getTo() == aHost; // Unicast Routing
	}

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost)
	{
		return m.getTo() != thisHost;
	}

	/*  for FBfriends */
	public boolean shouldSendMessageToFBHost(Message m, DTNHost otherHost)
	{	
		if(m.getTo() == otherHost) {
			m.updateProperty("meet", 3);
			return true;//FB朋友就是dest.
		}
		
		
		DTNHost dest = m.getTo();
		DistributedHybridLog de = getOtherDecisionEngine(otherHost);//取FB的decider看他的朋友
		
		
			
		/*如果這個FB朋友的FBlist有dest 傳*/
		SignalCost = SignalCost + de.FBfriends.size()*8;
		if(de.FBfriends.containsKey(dest)){	
			return true;
		}

		boolean peerInCommunity = de.commumesWithHost(dest);
		boolean meInCommunity = this.commumesWithHost(dest);
		SignalCost++;
		/*如果這個FB朋友在dest的群內而我不在 傳*/
		if(peerInCommunity && !meInCommunity){ // peer is in local commun. of dest
			return true;
		}
		
		SignalCost = SignalCost + de.onlymeetme.size()*8;
		if(de.onlymeetme.contains(m.getTo())){
			m.updateProperty("meet", 2);
			return true;
		}
		
		return false;
		
	}
	
	public boolean shouldSendMessageToHost(Message m, DTNHost otherHost)
	{
		
		if(m.getTo() == otherHost){
			m.updateProperty("meet", 3);
			return true; // trivial to deliver to final dest
		}
		
		/*
		 * Here is where we decide when to forward along a message. 
		 * 
		 * DiBuBB works such that it first forwards to the most globally central
		 * nodes in the network until it finds a node that has the message's 
		 * destination as part of it's local community. At this point, it uses 
		 * the local centrality metric to forward a message within the community. 
		 */
		DTNHost dest = m.getTo();
		DistributedHybridLog de = getOtherDecisionEngine(otherHost);
		
		
				
		// Which of us has the dest in our local communities, this host or the peer
		boolean peerInCommunity = de.commumesWithHost(dest);		
		boolean meInCommunity = this.commumesWithHost(dest);
		SignalCost++;
		if(peerInCommunity && !meInCommunity) // peer is in local commun. of dest
			return true;
		
		/*new*/
			/*如果這個朋友的FBlist有dest 傳*/
		
		else if(de.FBfriends.containsKey(dest)){
			SignalCost = SignalCost + de.FBfriends.size()*8;
			return true;
		}	
		
		/*new*/
		else if(!peerInCommunity && meInCommunity) // I'm in local commun. of dest	
			return false;
		else if(peerInCommunity) // we're both in the local community of destination
		{
			// Forward to the one with the higher local centrality (in our community)
			SignalCost = SignalCost + 8;
			if(de.getLocalCentrality() > this.getLocalCentrality())
				return true;
			/*else
				return false;*/
		}
		// Neither in local community, forward to more globally central node
		else if(de.getGlobalCentrality() > this.getGlobalCentrality()){
			SignalCost = SignalCost + 8;
			return true;
		}
		SignalCost = SignalCost + 8;
		if(de.onlymeetme.contains(m.getTo())){
			SignalCost = SignalCost + de.onlymeetme.size()*8;
			m.updateProperty("meet", 2);
			return true;
		}
		SignalCost = SignalCost + de.onlymeetme.size()*8;
		
		return false;
	}

	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
	{
		// DiBuBB allows a node to remove a message once it's forwarded it into the
		// local community of the destination
		DistributedHybridLog de = this.getOtherDecisionEngine(otherHost);
		return de.commumesWithHost(m.getTo()) && 
			!this.commumesWithHost(m.getTo());
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		DistributedHybridLog de = this.getOtherDecisionEngine(hostReportingOld);
		return de.commumesWithHost(m.getTo()) && 
			!this.commumesWithHost(m.getTo());
	}

	public RoutingDecisionEngine replicate()
	{
		return new DistributedHybridLog(this);
	}
	
	protected boolean commumesWithHost(DTNHost h)
	{
		return community.isHostInCommunity(h);
	}
	
	protected double getLocalCentrality()
	{
		return this.centrality.getLocalCentrality(connHistory, community);
	}
	
	protected double getGlobalCentrality()
	{
		return this.centrality.getGlobalCentrality(connHistory);
	}
	
	public double getNodeInTime(Map<DTNHost, List<Duration>> connHistory)
	{
			
		// initialize
		int[] centralities = new int[1];
		int epoch, timeNow = SimClock.getIntTime();
		Map<Integer, Set<DTNHost>> nodesCountedInEpoch = 
			new HashMap<Integer, Set<DTNHost>>();
		
		for(int i = 0; i < 1; i++)
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
				if(timePassed > 86400 * 1)
					break;
				
				// compute the epoch this contact belongs to
				epoch = timePassed / 86400;
				
				if (epoch >= 1) 
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
		for(int i = 0; i < 1; i++) 
			sum += centralities[i];
		double node = ((double)sum) / 1;			
		return node;
		
	}

	private DistributedHybridLog getOtherDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof HybridStrategyRouter : "This router only works " + 
		" with other routers of same type";
		
		return (DistributedHybridLog) ((HybridStrategyRouter)otherRouter).getDecisionEngine();
	}

	public Set<DTNHost> getLocalCommunity() {return this.community.getLocalCommunity();}
	
	/*new*/
	public Map<DTNHost, FBStatus> getFBfriends(){
		return this.FBfriends;
	}
	
	public void resetSignalCost(){
		SignalCost=0;
	}
}
