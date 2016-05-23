package routing.community;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import routing.DecisionEngineRouterFB;
import routing.HybridStrategyRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class DistributedBubbleRapFB implements RoutingDecisionEngine, CommunityDetectionEngine
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
	public DistributedBubbleRapFB(Settings s)
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
	public DistributedBubbleRapFB(DistributedBubbleRapFB proto)
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
			FBfriends.get(peer).Nrofcontact = FBfriends.get(peer).Nrofcontact+1;
		}
		if(!this.onlymeetme.contains(peer)){
			if(getOtherDecisionEngine(peer).FBfriends.isEmpty()){		
				this.onlymeetme.add(peer);
			}
			else if(getOtherDecisionEngine(peer).connHistory.size()<=threshold){
				this.onlymeetme.add(peer);
			}
		}
		else if(getOtherDecisionEngine(peer).connHistory.size()>threshold){
			this.onlymeetme.remove(peer);
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
		DistributedBubbleRapFB de = this.getOtherDecisionEngine(peer);
		
		this.startTimestamps.put(peer, SimClock.getTime());
		de.startTimestamps.put(myHost, SimClock.getTime());
		
		return this.community.newConnection(myHost, peer, de.community);
	}
	
	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
		double time = startTimestamps.get(peer);
		double etime = SimClock.getTime();
		
		// Find or create the connection history list
		List<Duration> history;
		if(!connHistory.containsKey(peer))
		{
			history = new LinkedList<Duration>();
			connHistory.put(peer, history);
		}
		else
			history = connHistory.get(peer);
		
		// add this connection to the list
		if(etime - time > 0)
			history.add(new Duration(time, etime));
		
		CommunityDetection peerCD = this.getOtherDecisionEngine(peer).community;
		
		// inform the community detection object that a connection was lost.
		// The object might need the whole connection history at this point.
		community.connectionLost(thisHost, peer, peerCD, history);
		
		startTimestamps.remove(peer);
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
		DistributedBubbleRapFB de = getOtherDecisionEngine(otherHost);//取FB的decider看他的朋友
		
		
			
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
		
		/*if(de.onlymeetme.contains(m.getTo())){
			m.updateProperty("meet", 2);
			return true;
		}*/
		
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
		DistributedBubbleRapFB de = getOtherDecisionEngine(otherHost);
		
		
				
		// Which of us has the dest in our local communities, this host or the peer
		boolean peerInCommunity = de.commumesWithHost(dest);	
		boolean meInCommunity = this.commumesWithHost(dest);
		SignalCost++;
		
		if(peerInCommunity && !meInCommunity) // peer is in local commun. of dest
			return true;
		
		/*new*/
			/*如果這個朋友的FBlist有dest 傳*/
		else if(de.FBfriends.containsKey(dest))	{
			SignalCost = SignalCost + de.FBfriends.size()*8;
			return true;}	
		/*new*/
		else if(!peerInCommunity && meInCommunity) // I'm in local commun. of dest	
			return false;
		else if(peerInCommunity) // we're both in the local community of destination
		{
			// Forward to the one with the higher local centrality (in our community)
			SignalCost = SignalCost + 8;
			if(de.getLocalCentrality() > this.getLocalCentrality())
				return true;
			else
				return false;
		}
		// Neither in local community, forward to more globally central node
		else if(de.getGlobalCentrality() > this.getGlobalCentrality()){
			SignalCost = SignalCost + 8;
			return true;
		}
		
		/*if(de.onlymeetme.contains(m.getTo())){
			m.updateProperty("meet", 2);
			return true;
		}*/
		SignalCost = SignalCost + 8;
		return false;
	}

	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost)
	{
		// DiBuBB allows a node to remove a message once it's forwarded it into the
		// local community of the destination
		DistributedBubbleRapFB de = this.getOtherDecisionEngine(otherHost);
		return de.commumesWithHost(m.getTo()) && 
			!this.commumesWithHost(m.getTo());
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		DistributedBubbleRapFB de = this.getOtherDecisionEngine(hostReportingOld);
		return de.commumesWithHost(m.getTo()) && 
			!this.commumesWithHost(m.getTo());
	}

	public RoutingDecisionEngine replicate()
	{
		return new DistributedBubbleRapFB(this);
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

	private DistributedBubbleRapFB getOtherDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof HybridStrategyRouter : "This router only works " + 
		" with other routers of same type";
		
		return (DistributedBubbleRapFB) ((DecisionEngineRouterFB)otherRouter).getDecisionEngine();
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



