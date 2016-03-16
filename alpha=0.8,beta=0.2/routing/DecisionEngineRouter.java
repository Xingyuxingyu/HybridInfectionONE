package routing;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;





import core.*;

/**
 * This class overrides ActiveRouter in order to inject calls to a 
 * DecisionEngine object where needed add extract as much code from the update()
 * method as possible. 
 * 
 * <strong>Forwarding Logic:</strong> 
 * 
 * A DecisionEngineRouter maintains a List of Tuple<Message, Connection> in 
 * support of a call to ActiveRouter.tryMessagesForConnected() in 
 * DecisionEngineRouter.update(). Since update() is called so frequently, we'd 
 * like as little computation done in it as possible; hence the List that gets
 * updated when events happen. Four events cause the List to be updated: a new 
 * message from this host, a new received message, a connection goes up, or a 
 * connection goes down. On a new message (either from this host or received 
 * from a peer), the collection of open connections is examined to see if the
 * message should be forwarded along them. If so, a new Tuple is added to the
 * List. When a connection goes up, the collection of messages is examined to 
 * determine to determine if any should be sent to this new peer, adding a Tuple
 * to the list if so. When a connection goes down, any Tuple in the list
 * associated with that connection is removed from the List.
 * 
 * <strong>Decision Engines</strong>
 * 
 * Most (if not all) routing decision making is provided by a 
 * RoutingDecisionEngine object. The DecisionEngine Interface defines methods 
 * that enact computation and return decisions as follows:
 * 
 * <ul>
 *   <li>In createNewMessage(), a call to RoutingDecisionEngine.newMessage() is 
 * 	 made. A return value of true indicates that the message should be added to
 * 	 the message store for routing. A false value indicates the message should
 *   be discarded.
 *   </li>
 *   <li>changedConnection() indicates either a connection went up or down. The
 *   appropriate connectionUp() or connectionDown() method is called on the
 *   RoutingDecisionEngine object. Also, on connection up events, this first
 *   peer to call changedConnection() will also call
 *   RoutingDecisionEngine.doExchangeForNewConnection() so that the two 
 *   decision engine objects can simultaneously exchange information and update 
 *   their routing tables (without fear of this method being called a second
 *   time).
 *   </li>
 *   <li>Starting a Message transfer, a protocol first asks the neighboring peer
 *   if it's okay to send the Message. If the peer indicates that the Message is
 *   OLD or DELIVERED, call to RoutingDecisionEngine.shouldDeleteOldMessage() is
 *   made to determine if the Message should be removed from the message store.
 *   <em>Note: if tombstones are enabled or deleteDelivered is disabled, the 
 *   Message will be deleted and no call to this method will be made.</em>
 *   </li>
 *   <li>When a message is received (in messageTransferred), a call to 
 *   RoutingDecisionEngine.isFinalDest() to determine if the receiving (this) 
 *   host is an intended recipient of the Message. Next, a call to 
 *   RoutingDecisionEngine.shouldSaveReceivedMessage() is made to determine if
 *   the new message should be stored and attempts to forward it on should be
 *   made. If so, the set of Connections is examined for transfer opportunities
 *   as described above.
 *   </li>
 *   <li> When a message is sent (in transferDone()), a call to 
 *   RoutingDecisionEngine.shouldDeleteSentMessage() is made to ask if the 
 *   departed Message now residing on a peer should be removed from the message
 *   store.
 *   </li>
 * </ul>
 * 
 * <strong>Tombstones</strong>
 * 
 * The ONE has the the deleteDelivered option that lets a host delete a message
 * if it comes in contact with the message's destination. More aggressive 
 * approach lets a host remember that a given message was already delivered by
 * storing the message ID in a list of delivered messages (which is called the
 * tombstone list here). Whenever any node tries to send a message to a host 
 * that has a tombstone for the message, the sending node receives the 
 * tombstone.
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public class DecisionEngineRouter extends ActiveRouter
{
	public static final String PUBSUB_NS = "DecisionEngineRouter";
	public static final String ENGINE_SETTING = "decisionEngine";
	public static final String TOMBSTONE_SETTING = "tombstones";
	public static final String CONNECTION_STATE_SETTING = "";
	
	protected boolean tombstoning;
	protected RoutingDecisionEngine decider;
	protected List<Tuple<Message, Connection>> outgoingMessages;
	
	protected boolean isBinary;
	protected int initialNrofCopies;
	public static final String MSG_COUNT_PROPERTY = "SPRAYANDWAIT_NS" + "." +"copies";
	public static final int copy = 2;
	public int CopyTemp=0;
	/*SprayAndWait End*/
	protected Set<String> tombstones;
	private List<Tuple<Message, Connection>> messages;
	
	/*Localized Bridging Centrality*/
	protected double LocalizedBridgingCentralityValue = 0;
	/*Localized Bridging Centrality*/
	
	
	/*Long Duration Contact*/
	public static final int th = 500;//cam
	//public static final int th = 200;//mit
	//public static final int th = 50;//pmtr
	
	public static final double alpha =0.8;
	public static final double beta =0.2;
	/*Long Duration Contact*/
	

	/*K-clique Decay*/
	/*private int minute = 60;
	private int hr = 60 * minute;
	private Set<DTNHost> markedForDeletion = null; 
	private Map<DTNHost,Double> LongTimeNoSee = null;
	private double DeletionThreshold = 0;
	private double checkWindow = 10 * minute;
	/*K-clique Decay*/

	/** 
	 * Used to save state machine when new connections are made. See comment in
	 * changedConnection() 
	 */
	protected Map<Connection, Integer> conStates;
	
	public DecisionEngineRouter(Settings s)
	{
		super(s);
		
		Settings routeSettings = new Settings(PUBSUB_NS);
		
		//outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		
		decider = (RoutingDecisionEngine)routeSettings.createIntializedObject("routing." + routeSettings.getSetting(ENGINE_SETTING));
		
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
		
		/*K-clique Decay*/
		/*this.markedForDeletion = new HashSet<DTNHost>();
		this.LongTimeNoSee = new HashMap<DTNHost,Double>();
		/*K-clique Decay*/
		
		/*Automatic Parameter*/

		/*if (s.contains(DeletionThreshold_SETTING)) 
			this.DeletionThreshold = s.getInt(DeletionThreshold_SETTING) * hr;
		
		/*Automatic Parameter*/
	}

	public DecisionEngineRouter(DecisionEngineRouter r)
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
		
		/*K-clique Decay*/
		/*this.markedForDeletion = new HashSet<DTNHost>();
		this.LongTimeNoSee = new HashMap<DTNHost,Double>();
		/*K-clique Decay*/
		
		/*this.DeletionThreshold = r.DeletionThreshold;
		*/
	}

	@Override
	public MessageRouter replicate()
	{
		return new DecisionEngineRouter(this);
	}

	@Override
	public boolean createNewMessage(Message m){
          if(decider.newMessage(m)){
              makeRoomForNewMessage(m.getSize());
              m.setTtl(this.msgTtl);
   			  /*SprayAndWait Begin*/
   			  m.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
   			  /*SprayAndWait End*/
              addToMessages(m, true); 
              
              /*masked by BubbleRapMixDraft begin*/
              //findConnectionsForNewMessage(m, getHost());
              /*masked by BubbleRapMixDraft end*/
              return true;
           }
           return false;
	}
	
	@Override
	public void changedConnection(Connection con)
	{
		DTNHost myHost = getHost();
		
		DTNHost otherNode = con.getOtherNode(myHost);
		DecisionEngineRouter otherRouter = (DecisionEngineRouter)otherNode.getRouter();

		
		if(con.isUp())
		{
			/*Betweenness*/
			//if (!this.NeighborList.contains(otherNode))
				//this.NeighborList.add(otherNode);
			/*Betweenness*/
			
			/*K-clique Decay*/
			/*if (this.LongTimeNoSee.containsKey(otherNode))
				this.LongTimeNoSee.remove(otherNode);
			if (this.markedForDeletion.contains(otherNode))
				this.markedForDeletion.remove(otherNode);
			/*K-clique Decay*/
			

			decider.connectionUp(myHost, otherNode);
			
			/*DRAFTCommunityDetection*/
			//myDRAFTCD.ConnectionUp(con, myHost);
			/*DRAFTCommunityDetection*/
			
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
			Collection<Message> msgs = getMessageCollection();
			/*for(Message m : msgs)
			{
				if(decider.shouldSendMessageToHost(m, otherNode)){
					outgoingMessages.add(new Tuple<Message,Connection>(m, con));
				}    
			}*/
		}
		else
		{
			/*Betweenness*/
			//if (this.NeighborList.contains(otherNode))
				//this.NeighborList.remove(otherNode);
			/*Betweenness*/
			
			/*K-clique Decay*/
			/*this.LongTimeNoSee.put(otherNode, 1.0);
			/*K-clique Decay*/

			
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
		decider.doExchangeForNewConnection(con, otherHost);
	}
	
	/**
	 * Called by a peer DecisionEngineRouter to indicated that it already 
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
			/*masked by BubbleRapMixDraft begin*/
			//findConnectionsForNewMessage(aMessage, from);
			/*masked by BubbleRapMixDraft end*/
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
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferred(aMessage, from, getHost(),
					isFirstDelivery);
		}
		
		return aMessage;
	}

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
		/*SprayAndWait  End*/	
	}

 @Override
 public void update(){
      super.update();      

      /*DRAFTCommunityDetection*/
      //myDRAFTCD.update(getConnections());
      /*DRAFTCommunityDetection*/
      
      /*K-clique Decay*/
     /* for (DTNHost h : this.LongTimeNoSee.keySet()){
    	  this.LongTimeNoSee.put(h, this.LongTimeNoSee.get(h)+1);
    	  if (this.LongTimeNoSee.get(h) > DeletionThreshold)
    		  markedForDeletion.add(h);
      }
      
        double simTime = SimClock.getTime(); // (seconds since start)
		double timeInFrame = simTime % this.checkWindow;
		if(timeInFrame == 0)  {
			for (DTNHost h : this.markedForDeletion)
				if (myDBR.getLocalCommunity().contains(h))
					myDBR.getLocalCommunity().remove(h);
			
			this.markedForDeletion.clear();
		}
      */
      
      /*K-clique Decay*/
            	
            	
      if (!canStartTransfer() || isTransferring()) {
           return; // nothing to transfer or is currently transferring 
      }
      
      tryOtherMessages();
      

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
	
	protected void findConnectionsForNewMessage(Message m, DTNHost from){
		/*masked by BubbleRapMixDraft begin*/
		/*for(Connection c : getConnections()){
			DTNHost other = c.getOtherNode(getHost());
			if(other != from && decider.shouldSendMessageToHost(m, other)){
				
				//outgoingMessages.add(new Tuple<Message, Connection>(m, c));
				
			}
		}*/
		/*masked by BubbleRapMixDraft end*/
	}
	/*a part of BubbleRapMixDraft begin*/
	private Tuple<Message, Connection> tryOtherMessages() {
		messages = new ArrayList<Tuple<Message, Connection>>(); 
		Collection<Message> msgCollection = getMessageCollection();
		
		//DistributedBubbleRap MyDBR = (DistributedBubbleRap) decider;
		//MyDBR.updateLBC();
		
		
		
	
		
		/*sort connections from greatest to smallest*/
		List<Connection> newCons = getConnections();
		/*Collections.sort(newCons,
        new Comparator<Connection>() {
            public int compare(Connection con1, Connection con2) {
            	DTNHost h1 = con1.getOtherNode(getHost());
            	DecisionEngineRouter h1DER = (DecisionEngineRouter) h1.getRouter();
            	DistributedBubbleRap h1DBR = (DistributedBubbleRap) h1DER.getDecider();
            	double h1CLBC = h1DBR.getCumulativeLocalizedBridgingCentrality();
           
            	DTNHost h2 = con2.getOtherNode(getHost());
            	DecisionEngineRouter h2DER = (DecisionEngineRouter) h2.getRouter();
            	DistributedBubbleRap h2DBR = (DistributedBubbleRap) h2DER.getDecider();
            	double h2CLBC = h2DBR.getCumulativeLocalizedBridgingCentrality();
            	
            	if (h1CLBC<h2CLBC)
            		return 1;
            	else if (h1CLBC>h2CLBC)
            		return -1;
            	else 
            		return 0;
            }
        });*/
		


		for (Message m : msgCollection) {
			for (Connection con : newCons) {
				DTNHost other = con.getOtherNode(getHost());
				DecisionEngineRouter otherRouter = (DecisionEngineRouter)other.getRouter();
				DecisionEngineRouter myRouter = (DecisionEngineRouter)getHost().getRouter();
				if (otherRouter.isTransferring())
					continue; // skip host which is transferring
				if (otherRouter.hasMessage(m.getId())) 
					continue; // skip message if the peer already have
				if(otherRouter != myRouter && decider.shouldSendMessageToHost(m, other)){
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}
		}
		if (messages.size() == 0) {
			return null;
		}
		/*SprayAndWait  Begin*/
		for(Iterator<Tuple<Message, Connection>> i = messages.iterator(); 
				i.hasNext();){		
				Tuple<Message, Connection> t = i.next();
				Integer nrofCopies = (Integer)t.getKey().getProperty(MSG_COUNT_PROPERTY);
				if(nrofCopies <= 0){	
					i.remove();	
				}
		}
		/*SprayAndWait  End*/ 
		
		
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	/*a part of BubbleRapMixDraft end*/
	
	/*Betweenness*/
	/*public void findDestPath(DTNHost current,DTNHost dest,RoutingPath path,List<RoutingPath> availablePaths) throws CloneNotSupportedException{
		int NofNeighbors = current.getRouter().NeighborList.size();
		path.add(current);
		if (current==dest){
			availablePaths.add(path);
		}
		else{
			for (int i = 0 ; i < NofNeighbors;i++){
				DTNHost next = (DTNHost) current.getRouter().NeighborList.get(i);
				if (!path.contains(next) && NofNeighbors>=3){//one in the back and two(or more) in the forward
					RoutingPath Clonepath = (RoutingPath) path.clone();
					findDestPath(next,dest,Clonepath,availablePaths);
				}
				else if (!path.contains(next) && NofNeighbors<3 )//one in the back and one in the forward
					findDestPath(next,dest,path,availablePaths);
			}
		}
	}*/
	/*Betweenness*/
	
	public RoutingDecisionEngine getDecider(){
		return this.decider;
	}
	public double getLocalizedBridgingCentralityValue(){
		return this.LocalizedBridgingCentralityValue;
	}
	public void setLocalizedBridgingCentralityValue(double value){
		this.LocalizedBridgingCentralityValue = value;
	}
	public int getCopyTmep(){
		return this.CopyTemp;
	}
	public void setCopyTemp(int temp){
		this.CopyTemp = temp;
	}
}
