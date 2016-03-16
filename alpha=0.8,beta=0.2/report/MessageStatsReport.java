/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.*;
import core.DTNHost;
import core.Message;
import core.MessageListener;

/**
 * Report for generating different kind of total statistics about message
 * relaying performance. Messages that were created during the warm up period
 * are ignored.
 * <P><strong>Note:</strong> if some statistics could not be created (e.g.
 * overhead ratio if no messages were delivered) "NaN" is reported for
 * double values and zero for integer median(s).
 */
public class MessageStatsReport extends Report implements MessageListener {
	private Map<String, Double> creationTimes;
	private List<Double> latencies;
	private List<Integer> hopCounts;
	private List<Double> msgBufferTime;
	private List<Double> rtt; // round trip times
	private LinkedHashMap< String, Double >meslatences;
	private int nrofDropped;
	private int nrofRemoved;
	private int nrofStarted;
	private int nrofAborted;
	private int nrofRelayed;
	private int nrofCreated;
	private int nrofResponseReqCreated;
	private int nrofResponseDelivered;
	private int nrofDelivered;
	private int Global_centralityrRelay;
	private int local_centralityrRelay;
	private Map<String, Integer> ArrivalTime;
	public Integer a[][]; 
	public Map<String, Integer>popularnode;
	public int forward[][];
	public List<String> msginfo ;
	public Map<String, List<String>>node_path;
	
	/**
	 * Constructor.
	 */
	public MessageStatsReport() {
		init();
	}

	@Override
	protected void init() {
		super.init();
		this.creationTimes = new HashMap<String, Double>();
		this.latencies = new ArrayList<Double>();
		this.msgBufferTime = new ArrayList<Double>();
		this.hopCounts = new ArrayList<Integer>();
		this.rtt = new ArrayList<Double>();
		meslatences = new  LinkedHashMap< String, Double >();
		ArrivalTime = new  HashMap< String, Integer >();
		this.nrofDropped = 0;
		this.nrofRemoved = 0;
		this.nrofStarted = 0;
		this.nrofAborted = 0;
		this.nrofRelayed = 0;
		this.nrofCreated = 0;
		this.nrofResponseReqCreated = 0;
		this.nrofResponseDelivered = 0;
		this.nrofDelivered = 0;
		
		this.Global_centralityrRelay = 0;
		this.local_centralityrRelay = 0;
		this.popularnode = new HashMap<String, Integer>();
		a = new Integer [1000][2];
		forward = new int[100][1]; 
		for( int i = 0; i < 1000 ;i++)
		{
			for( int j = 0; j < 2; j++  )
			{
				a[i][j] = new Integer(0);
			}
		}
		this.msginfo = new LinkedList<String>();
		this.node_path = new HashMap<String, List<String>>();
	}

	
	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		if (isWarmupID(m.getId())) {
			return;
		}
		
		if (dropped) {
			this.nrofDropped++;
		}
		else {
			this.nrofRemoved++;
		}
		
		this.msgBufferTime.add(getSimTime() - m.getReceiveTime());
	}

	
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}
		
		this.nrofAborted++;
	}
	public void cc( int rule,boolean finalTarget,Message m, DTNHost from, DTNHost to)		
	{
		if( rule == 4 )
		{
			Global_centralityrRelay++;
		}else if( rule == 3 || rule == 2 )
		{
			local_centralityrRelay++;
		}
		if (finalTarget) {
		node_path.get(m.toString()).add( from.toString().substring(1)+"->"+to.toString().substring(1) + "  [ label = " + rule + "]" );

		node_path.get(m.toString()).add( "D" );
		}
		else
		{
			//npath.add( from.toString()+"->"+to.toString()+"; relayed");
			node_path.get(m.toString()).add(from.toString().substring(1)+"->"+to.toString().substring(1) + "  [ label = " + rule + "]" );
		}
		//this.node_path.put(m.getId(), npath);
		
	}

	
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
			boolean finalTarget) {
		if (isWarmupID(m.getId())) {
			return;
		}
		int y = (int)(Integer.parseInt(from.toString().substring(1))+1 );
		//int k = (int)(Integer.parseInt(to.toString().substring(1))+1 );
		forward[y][0]++;
		//forward[k][0]++;
		
		Integer i = Integer.parseInt(m.getId().substring(1))-1 ;
		
		a[i][1]++;
		
		this.nrofRelayed++;
		if (finalTarget) {
			a[i][0] = 1;
			this.latencies.add(getSimTime() - 
				this.creationTimes.get(m.getId()) );
			this.meslatences.put( m.getId(), getSimTime() - this.creationTimes.get(m.getId()) )	;
			int time = (int) getSimTime();
			this.ArrivalTime.put( m.getId(), new Integer( time ) )	;	
			this.nrofDelivered++;
			this.hopCounts.add(m.getHops().size() - 1);
			
			List<DTNHost> mylist = m.getHops();
			for( DTNHost node: mylist )
			{
				if( node.toString() != m.getFrom().toString() && node.toString() != m.getTo().toString() )
				{
					if( !popularnode.containsKey(node.toString()) )
					{
						popularnode.put(node.toString(), new Integer(1));
					}else
					{
						Integer j = popularnode.get(node.toString());
						j = j + 1;
						popularnode.put(node.toString(), j);
					}
				}
			}
			
			if (m.isResponse()) {
				this.rtt.add(getSimTime() -	m.getRequest().getCreationTime());
				this.nrofResponseDelivered++;
			}
		}
	}


	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getId());
			return;
		}
		
		this.msginfo.add(getSimTime()+" "+m.getId()+" "+m.getFrom().toString().substring(1)+" "+m.getTo().toString().substring(1));
		this.creationTimes.put(m.getId(), getSimTime());
		this.nrofCreated++;
		if (m.getResponseSize() > 0) {
			this.nrofResponseReqCreated++;
		}
	}
	
	
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		if (isWarmupID(m.getId())) {
			return;
		}
		List<String> npath;
		if(!node_path.containsKey(m.toString()))
		{
			npath = new LinkedList<String>();
			npath.add(m.getFrom().toString().substring(1)+" " +m.getTo().toString().substring(1) + " Y");
			node_path.put(m.toString(), npath);
		}

		this.nrofStarted++;
	}
	

	@Override
	public void done() {
		/*write("Message stats for scenario " + getScenarioName() + 
				"\nsim_time: " + format(getSimTime()));*/
		double deliveryProb = 0; // delivery probability
		double responseProb = 0; // request-response success probability
		double overHead = Double.NaN;	// overhead ratio
		double deliverycost = 0;
		if (this.nrofCreated > 0) {
			deliveryProb = (1.0 * this.nrofDelivered) / this.nrofCreated;
		}
		if (this.nrofDelivered > 0) {
			overHead = (1.0 * (this.nrofRelayed - this.nrofDelivered)) /
				this.nrofDelivered;
		}
		if (this.nrofResponseReqCreated > 0) {
			responseProb = (1.0* this.nrofResponseDelivered) / 
				this.nrofResponseReqCreated;
		}
		if (this.nrofCreated > 0) {
			deliverycost = (1.0 * (this.nrofRelayed )) /
				this.nrofCreated;
		}
		

		
		/*String statsText =
		    "\ndelivery_prob: " + format(deliveryProb) +			
			"\ndeliverycost: " + format(deliverycost) +
		    "created: " + this.nrofCreated + 
			"\nstarted: " + this.nrofStarted + 
			"\nrelayed: " + this.nrofRelayed +
			"\nGlobal_centralityrRelay: " + this.Global_centralityrRelay +
			"\nlocal_centralityrRelay: " + this.local_centralityrRelay +
			"\naborted: " + this.nrofAborted +
			"\ndropped: " + this.nrofDropped +
			"\nremoved: " + this.nrofRemoved +
			"\ndelivered: " + this.nrofDelivered +
			"\nresponse_prob: " + format(responseProb) + 		
			"\noverhead_ratio: " + format(overHead) + 
			"\nlatency_avg: " + getAverage(this.latencies) +
			"\nlatency_med: " + getMedian(this.latencies) + 
			"\nhopcount_avg: " + getIntAverage(this.hopCounts) +
			"\nhopcount_med: " + getIntMedian(this.hopCounts) + 
			"\nbuffertime_avg: " + getAverage(this.msgBufferTime) +
			"\nbuffertime_med: " + getMedian(this.msgBufferTime) +
			"\nrtt_avg: " + getAverage(this.rtt) +
			"\nrtt_med: " + getMedian(this.rtt)
			;
		*/
		
		String statsText =
		     format(deliveryProb) +	"\n"	+ format(deliverycost) ;	
			 
		write(statsText);
		
		/*for(Map.Entry<String, List<String>> entry : node_path.entrySet())
		{
			String mesagetxt = entry.getKey().substring(1);
			write( mesagetxt + " " );
			
			List<String> pathtxt = entry.getValue();
			
			for( String s : pathtxt )
			{
				write(s);
			}
			write("\n");
		}
		write("\n");
		*/
	/*	for( String key:  this.ArrivalTime.keySet())
		{
			write( key.substring(1) + "\t" + this.ArrivalTime.get(key) );
		}*/
		
		/*for(String str : this.msginfo)	
		{
			write(str);
		}*/
			
		
		
		/*for( int i = 0; i < 100;i++ )
		{
			write( "" + forward[i][0] );
		}*/
		
		
		/*write("");
		
		for( String key:  this.ArrivalTime.keySet())
		{
			write( key.substring(1) + "\t" + this.ArrivalTime.get(key) );
		}
		
		write("");
		*/
				
		/*for( String s : this.popularnode.keySet())
		{
			write( s.substring(1) + " " + popularnode.get(s));
		}
		write("");*/
		/*
		
		/*Set set = meslatences.entrySet(); 
		Iterator i = set.iterator(); 
		while(i.hasNext()) { 
			Map.Entry me = (Map.Entry)i.next(); 
			  write(me.getKey() + " " + me.getValue());
		}*/
		/*for(double time : this.latencies)
		{
			write( format(time) );
		}*/
		/*write( "----------------------------------------------------------" );
		for( int i = 0; i < 1000; i++ )
		{		
			if( a[i][0] == 0 )
				write( format(a[i][1]) );
		}
		
		write( "----------------------------------------------------------" );
		
		for( int i = 0; i < 1000; i++ )
		{		
			if( a[i][0] == 1 )
				write( format(a[i][1]) );
		}*/
	
		
		
		super.done();
	}
	
}