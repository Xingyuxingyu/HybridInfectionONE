/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import input.Friend;

import java.util.ArrayList;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;


/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class EpidemicRouter extends ActiveRouter {
	
	
	/*Bei*/
	private double DTNInfectionRate = 0.001;
	private double OSNInfectionRate = 0.001;
	//private int OSNInterval = 1;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EpidemicRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EpidemicRouter(EpidemicRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}
			
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		// then try any/all message to any/all connection
		this.tryAllMessagesToAllConnections();
	}
	
	
	@Override
	public EpidemicRouter replicate() {
		return new EpidemicRouter(this);
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
	
	@Override
	protected Connection tryAllMessagesToAllConnections(){
		//add friendList(everything about friend)//
		List<Friend> friends = getFriends();
		List<Connection> connections = getConnections();
		/*friend*/
		if ((friends.size() == 0 && connections.size() == 0) || this.getNrofMessages() == 0) {
			return null;
		}

		List<Message> messages = 
			new ArrayList<Message>(this.getMessageCollection());
		this.sortByQueueMode(messages);

		return tryMessagesToConnections(messages, connections, friends);
	}
	
	
	protected Connection tryMessagesToConnections(List<Message> messages,
			List<Connection> connections,List<Friend> friends) {

		/*for (int i=0, n=connections.size(); i<n; i++) {

			Connection con = connections.get(i);
			con.setInfectionRate(DTNInfectionRate);
			Message started = tryAllMessages(con, messages); 
			if (started != null) { 
				return con;
			}
		}*/
		/*testForFriend*/
		//if(SimClock.getIntTime() % OSNInterval == 0){
			/*for (int j=0, m=friends.size(); j<m; j++) {
				Friend friend = friends.get(j);
				DTNHost fromNode = friend.getFromHost();
				DTNHost toNode = friend.getToHost();
				NetworkInterface fromInter= friend.getFromInterface();
				NetworkInterface toInter= friend.getToInterface();
			
				Connection friendcon = new CBRConnection(fromNode, fromInter, toNode, toInter,1);
				friendcon.setUpState(true);
				friendcon.setInfectionRate(OSNInfectionRate);
			
				Message startedFriend = tryAllMessages(friendcon, messages); 
				if (startedFriend != null) { 
				return friendcon;
				}
		
			}*/
		//}
		
		/*testForFriend*/
		
			for (int i=0, n=connections.size(); i<n; i++) {

				Connection con = connections.get(i);

				Message started = tryAllMessages(con, messages); 
				if (started != null) { 
					return con;
				}
			}
		
		return null;
	}
	

	protected int checkReceiving(Message m ,DTNHost from) {
		if (isTransferring()) {
			return TRY_LATER_BUSY; // only one connection at a time
		}
	
		if ( hasMessage(m.getId()) || isDeliveredMessage(m) ){
			return DENIED_OLD; // already seen this message -> reject it
		}
		
		if (m.getTtl() <= 0 && m.getTo() != getHost()) {
			/* TTL has expired and this host is not the final recipient */
			return DENIED_TTL; 
		}

		/* remove oldest messages but not the ones being sent */
		if (!makeRoomForMessage(m.getSize())) {
			return DENIED_NO_SPACE; // couldn't fit into buffer -> reject
		}
		
		for (Connection con : from.getInterfaces().get(0).getConnections()){			
			if (con.getOtherNode(this.getHost())== from && con.getOtherNode(from)== this.getHost()){
				//System.out.println("3");
				if (Math.random() < OSNInfectionRate){
					//System.out.println("4");
					return RCV_OK;
				}
			}
		}
		
		for (Connection con : from.getInterfaces().get(1).getConnections()){
			//System.out.println("!");
			if (con.getOtherNode(this.getHost())== from && con.getOtherNode(from)== this.getHost()){
				//System.out.println("1");
				if (Math.random() < DTNInfectionRate){
					//System.out.println("2");
					return RCV_OK;
				}
			}
		}
		
		
		
		/*for (Connection con : from.getConnections()){
			if (con.getOtherNode(this.getHost())== from && con.getOtherNode(from)== this.getHost()){
				if (Math.random() < DTNInfectionRate){
					return RCV_OK;
				}
				else return DENIED_UNINFECTED;
			}
		}
		
		if (Math.random() < OSNInfectionRate){
			return RCV_OK;
		}*/
								
		return DENIED_UNINFECTED;
	}
	
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		int recvCheck = checkReceiving(m ,from); 
		if (recvCheck != RCV_OK) {
			return recvCheck;
		}
		
		Message newMessage = m.replicate();
		
		this.putToIncomingBuffer(newMessage, from);		
		newMessage.addNodeOnPath(getHost());
		
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferStarted(newMessage, from, getHost());
		}
		
		return RCV_OK;
	}

	

}