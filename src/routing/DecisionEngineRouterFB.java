package routing;

import input.Friend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import routing.community.DistributedBubbleRapFB;
import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.Tuple;

public class DecisionEngineRouterFB extends ActiveRouter
{
	public static final String PUBSUB_NS = "DecisionEngineRouterFB";
	public static final String ENGINE_SETTING = "decisionEngine";
	public static final String TOMBSTONE_SETTING = "tombstones";
	public static final String CONNECTION_STATE_SETTING = "";
	protected boolean tombstoning;
	public RoutingDecisionEngine decider;
	protected List<Tuple<Message, Connection>> outgoingMessages;
	/*SprayAndWait Begin*/
	protected int initialNrofCopies;
	public static final String MSG_COUNT_PROPERTY = "SPRAYANDWAIT_NS" + "." +"copies";
	public static final int copy = 2;
	public int CopyTemp=0;
	/*SprayAndWait End*/
	protected Set<String> tombstones;
	private List<Tuple<Message, Connection>> ShouldSendMessages;
	public int readflag = 0;
	public int SignalCost = 0;



	/** 
	 * Used to save state machine when new connections are made. See comment in
	 * changedConnection() 
	 */
	protected Map<Connection, Integer> conStates;
	
	public DecisionEngineRouterFB(Settings s)
	{
		super(s);
		
		Settings routeSettings = new Settings(PUBSUB_NS);
		
		
		//outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		
		decider = (RoutingDecisionEngine)routeSettings.createIntializedObject(
				"routing." + routeSettings.getSetting(ENGINE_SETTING));
		
		if(routeSettings.contains(TOMBSTONE_SETTING))
			tombstoning = routeSettings.getBoolean(TOMBSTONE_SETTING);
		else
			tombstoning = false;
		
		if(tombstoning)
			tombstones = new HashSet<String>(10);
		conStates = new HashMap<Connection, Integer>(4);
		/*SprayAndWait Begin*/
		initialNrofCopies = copy ;
		/*SprayAndWait End*/
		//decider.FBread(getHost());
	}

	public DecisionEngineRouterFB(DecisionEngineRouterFB r)
	{
		super(r);
		//outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		decider = r.decider.replicate();
		tombstoning = r.tombstoning;
		
		if(this.tombstoning)
			tombstones = new HashSet<String>(10);
		conStates = new HashMap<Connection, Integer>(4);
		/*SprayAndWait Begin*/
		this.initialNrofCopies = copy ;
		/*SprayAndWait End*/
		//decider.FBread(getHost());
	}

	@Override
	public MessageRouter replicate()
	{
		return new DecisionEngineRouterFB(this);
	}

	@Override
	public boolean createNewMessage(Message m){
          if(decider.newMessage(m)){
              makeRoomForNewMessage(m.getSize());
              m.setTtl(this.msgTtl);
   			  /*SprayAndWait Begin*/
   			  m.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
   			  m.addProperty("meet", 0);
   			  /*SprayAndWait End*/
              addToMessages(m, true); 
              
              /*mask for BubbleRapMixDraft begin*/
              //findConnectionsForNewMessage(m, getHost());
              /*mask for BubbleRapMixDraft end*/
              return true;
           }
           return false;
	}
	
	@Override
	public void changedConnection(Connection con)
	{
		DTNHost myHost = getHost();
		DTNHost otherNode = con.getOtherNode(myHost);
		DecisionEngineRouterFB otherRouter = (DecisionEngineRouterFB)otherNode.getRouter();
		if(con.isUp())
		{
			/*Betweenness*/
			//if (!this.NeighborList.contains(otherNode))
				//this.NeighborList.add(otherNode);
			/*Betweenness*/
			

			decider.connectionUp(myHost, otherNode);
			
			/*
			 * This part is a little confusing because there's a problem we have to
			 * avoid. When a connection comes up, we're assuming here that the two 
			 * hosts who are now connected will exchange some routing information and
			 * update their own based on what the get from the peer. So host A updates
			 * its routing table with info from host B, and vice versa. In the real
			 * world, A would send its *old* routing information to B and compute new
			 * routing information later after receiving B's *old* routing information.
			 * In ONE, changedConnection() is called twice, once for each host A and
			 * B, in a serial fashion. If it's called for A first, A uses B's old info
			 * to compute its new info, but B later uses A's *new* info to compute its
			 * new info.... and this can lead to some nasty problems. 
			 * 
			 * To combat this, whichever host calls changedConnection() first calls
			 * doExchange() once. doExchange() interacts with the DecisionEngine to
			 * initiate the exchange of information, and it's assumed that this code
			 * will update the information on both peers simultaneously using the old
			 * information from both peers.
			 */
			if(shouldNotifyPeer(con))
			{
				this.doExchange(con, otherNode);
				otherRouter.didExchange(con);
			}
			
			/*
			 * Once we have new information computed for the peer, we figure out if
			 * there are any messages that should get sent to this peer.
			 */
			/*Collection<Message> msgs = getMessageCollection();
			for(Message m : msgs)
			{
				if(decider.shouldSendMessageToHost(m, otherNode)){
					outgoingMessages.add(new Tuple<Message,Connection>(m, con));
				}    
			}*/
		}
		else
		{

			decider.connectionDown(myHost, otherNode);
			conStates.remove(con);
			
			/*
			 * If we  were trying to send message to this peer, we need to remove them
			 * from the outgoing List.
			 */
			/*for(Iterator<Tuple<Message,Connection>> i = outgoingMessages.iterator(); 
					i.hasNext();)
			{
				Tuple<Message, Connection> t = i.next();
				if(t.getValue() == con)
					i.remove();
			}*/
		}
	}
	


	protected void doExchange(Connection con, DTNHost otherHost)
	{
		conStates.put(con, 1);
		int newCost = decider.doExchangeForNewConnection(con, otherHost);
		SignalCost = newCost + SignalCost;
	}
	
	/**
	 * Called by a peer HybridStrategyRouter to indicated that it already 
	 * performed an information exchange for the given connection.
	 * 
	 * @param con Connection on which the exchange was performed
	 */
	protected void didExchange(Connection con)
	{
		conStates.put(con, 1);
	}
	
	@Override
	protected int startTransfer(Message m, Connection con)
	{
		int retVal;
		
		if (!con.isReadyForTransfer()) {
			return TRY_LATER_BUSY;
		}
		
		retVal = con.startTransfer(getHost(), m);
		if (retVal == RCV_OK) { // started transfer
			addToSendingConnections(con);	
		}
		else if(tombstoning && retVal == DENIED_DELIVERED)
		{
			this.deleteMessage(m.getId(), false);
			tombstones.add(m.getId());
		}
		else if (deleteDelivered && (retVal == DENIED_OLD || retVal == DENIED_DELIVERED) && 
				decider.shouldDeleteOldMessage(m, con.getOtherNode(getHost()))) {
			/* final recipient has already received the msg -> delete it */
			this.deleteMessage(m.getId(), false);
		}
		
		return retVal;
	}

	@Override
	public int receiveMessage(Message m, DTNHost from){
           if(isDeliveredMessage(m) || (tombstoning && tombstones.contains(m.getId())))
               return DENIED_DELIVERED; 
           
           return super.receiveMessage(m, from);
	}
	//receiver
	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message incoming = removeFromIncomingBuffer(id, from);
	
		if (incoming == null) {
			throw new SimError("No message with ID " + id + " in the incoming "+
					"buffer of " + getHost());
		}
		
		incoming.setReceiveTime(SimClock.getTime());
		
		Message outgoing = incoming;
		for (Application app : getApplications(incoming.getAppID())) {
			// Note that the order of applications is significant
			// since the next one gets the output of the previous.
			outgoing = app.handle(outgoing, getHost());
			if (outgoing == null) break; // Some app wanted to drop the message
		}
		
		Message aMessage = (outgoing==null)?(incoming):(outgoing);
		
		boolean isFinalRecipient = decider.isFinalDest(aMessage, getHost());
		boolean isFirstDelivery =  isFinalRecipient && 
			!isDeliveredMessage(aMessage);
		
		if (outgoing!=null && decider.shouldSaveReceivedMessage(aMessage, getHost())) 
		{
			// not the final recipient and app doesn't want to drop the message
			// -> put to buffer
			addToMessages(aMessage, false);
			// Determine any other connections to which to forward a message
		}
		
	
		if (isFirstDelivery)
		{
			this.deliveredMessages.put(id, aMessage);			
		}
		
		/*SprayAndWait  Begin*/
		Integer nrofCopies = (Integer)aMessage.getProperty(MSG_COUNT_PROPERTY);
		if (nrofCopies <= 1)
			nrofCopies = 1;
		else
			nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
		aMessage.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		/*SprayAndWait  End*/
		
		SignalCost = ((DistributedBubbleRapFB)decider).SignalCost + SignalCost;
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferred(aMessage, from, getHost(),
					isFirstDelivery, SignalCost);
			SignalCost = 0;
			((DistributedBubbleRapFB)decider).resetSignalCost();
		}
		
		return aMessage;
	}
	//sender
	@Override
	protected void transferDone(Connection con)
	{
		Message transferred = this.getMessage(con.getMessage().getId());
	
		/*for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
		i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			if(t.getKey().getId().equals(transferred.getId()) && 
					t.getValue().equals(con))
			{
				i.remove();
				break;
			}
		}*/		
		boolean flag=false;
		if(decider.shouldDeleteSentMessage(transferred, con.getOtherNode(getHost())))
		{
			this.deleteMessage(transferred.getId(), false);
			/*for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
			i.hasNext();)
			{
				Tuple<Message, Connection> t = i.next();
				if(t.getKey().getId().equals(transferred.getId()))
				{
					i.remove();
				}
			}*/
		}	
		/*SprayAndWait  Begin*/
		/* reduce the amount of copies left */
		Integer nrofCopies;
		nrofCopies = (Integer)transferred.getProperty(MSG_COUNT_PROPERTY);
		
		if( nrofCopies<= 1 ){
			nrofCopies = 0;
		}else
			nrofCopies /= 2;
		
		transferred.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		if (flag)
			System.out.println(transferred);
		/*SprayAndWait  End*/	
	}

 @Override
 public void update(){
      super.update();
      /*new for read FBList*/
      if(readflag==0){
    	  decider.FBread(getHost());
    	  readflag = 1 ;
      }
		
      if (!canStartTransfer() || isTransferring()) {
           return; // nothing to transfer or is currently transferring 
      }

     /* for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); i.hasNext();){
            Tuple<Message, Connection> t = i.next();
            if(!this.hasMessage(t.getKey().getId())){
                   i.remove();
           }
      }*/
      
	  /*the part of BubbleRapMixDraft begin*/
      tryOtherMessages();
      /*the part of BubbleRapMixDraft end*/

 }

	
	public RoutingDecisionEngine getDecisionEngine()
	{
		return this.decider;
	}

	protected boolean shouldNotifyPeer(Connection con)
	{
		Integer i = conStates.get(con);
		return i == null || i < 1;
	}
	
	/*the part of BubbleRapMixDraft begin*/
	private Tuple<Message, Connection> tryOtherMessages() {
		ShouldSendMessages = new ArrayList<Tuple<Message, Connection>>(); 
		Collection<Message> msgCollection = getMessageCollection();
		

		for (Message m : msgCollection) {
			List<DTNHost> remember = new ArrayList<DTNHost>();
			remember.add(getHost());
			
			int meet = (Integer) m.getProperty("meet");
			
			/*檢查DTN的人要不要傳*/

			for (Connection con : getConn()) {
				DTNHost other = con.getOtherNode(getHost());
				DecisionEngineRouterFB otherRouter = (DecisionEngineRouterFB)other.getRouter();
				DecisionEngineRouterFB myRouter = (DecisionEngineRouterFB)getHost().getRouter();
				if (otherRouter.isTransferring())
					continue; // skip host which is transferring
				if (otherRouter.hasMessage(m.getId())) 
					continue; // skip message if the peer already have
				if(otherRouter != myRouter && decider.shouldSendMessageToHost(m, other)){
					/*如果DTN有同樣的人傳 就不再傳*/
					remember.add(other);
					ShouldSendMessages.add(new Tuple<Message, Connection>(m,con));
					m.updateProperty("meet", meet);				
				}
			}
			
			/*檢查FB有沒有目的地*/
			
			
			for (Connection con : getFriendsCon()) {
				DTNHost other = con.getOtherNode(getHost());
				DecisionEngineRouterFB otherRouter = (DecisionEngineRouterFB)other.getRouter();
				DecisionEngineRouterFB myRouter = (DecisionEngineRouterFB)getHost().getRouter();
				if (otherRouter.isTransferring())
					continue; // skip host which is transferring
				if (otherRouter.hasMessage(m.getId())) 
					continue; // skip message if the peer already have
				if(otherRouter != myRouter && decider.shouldSendMessageToFBHost(m, other)){	
					//m.addnrofFBtrans(1);
					if(!remember.contains(other)){
					con.changeFBedge(true);
					ShouldSendMessages.add(new Tuple<Message, Connection>(m,con));
					m.updateProperty("meet", meet);	
					}
				}	
			}
			
		}
		if (ShouldSendMessages.size() == 0) {
			return null;
		}
		/*SprayAndWait  Begin*/
		for(Iterator<Tuple<Message, Connection>> i = ShouldSendMessages.iterator(); 
				i.hasNext();){		
				Tuple<Message, Connection> t = i.next();
				Integer nrofCopies = (Integer)t.getKey().getProperty(MSG_COUNT_PROPERTY);				
				if(nrofCopies <= 0){						
					i.remove();						
				}
				
		}
		/*SprayAndWait  End*/ 
		
		return tryMessagesForConnected(ShouldSendMessages);	// try to send messages
		
	}
	
	/*the part of BubbleRapMixDraft end*/
	

	protected void findConnectionsForNewMessage(Message m, DTNHost from){
	
	}

	public RoutingDecisionEngine getDecider(){
		return this.decider;
	}

	public int getCopyTmep(){
		return this.CopyTemp;
	}
	
	@Override
	protected boolean canStartTransfer() {
		if (this.getNrofMessages() == 0) {
			return false;
		}
		if (this.getConnections().size() == 0 && this.getFriends().size() == 0) {
			return false;
		}
		
		return true;
	}
	
	protected List<Friend> getFriends() {
		return getHost().getInterfaces().get(0).getFriends();
	}
	
	protected List<Connection> getFriendsCon(){
		return getHost().getInterfaces().get(0).getConnections();
	}
	
	protected List<Connection> getConn(){
		return getHost().getInterfaces().get(1).getConnections();
	}
	
	public void addSignalCost(int newCost){
		SignalCost = SignalCost + newCost;
	}

}

