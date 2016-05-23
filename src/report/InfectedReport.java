package report;

import java.util.*;

import core.DTNHost;
import core.Message;
import core.MessageListener;

public class InfectedReport extends Report implements MessageListener {
	private int infected;
	private List<Integer> infectedList ;
	
	public InfectedReport() {
		init();
		this.infected = 0;
		this.infectedList = new ArrayList<Integer> ();
	}

	@Override
	public void newMessage(Message m) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		// TODO Auto-generated method stub
		if (!this.infectedList.contains(to.getAddress())){
			this.infectedList.add(to.getAddress());
			infected++;
			reportValues(from.getAddress() , to.getAddress());
		}
	}

	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to,
			boolean firstDelivery) {
		// TODO Auto-generated method stub
		/*if (!this.infectedList.contains(to.getAddress())){
			this.infectedList.add(to.getAddress());
			infected++;
			reportValues();
		}	*/	
		
		
	}	
	
	public void TransferDone(DTNHost to){
		
	}
	
	public void done() {
		super.done();
	}
	
	private void reportValues(int from, int to) {
		//double prob = (1.0 * delivered) / created;
		write(format(getSimTime()) + "	" + infected +"	"+from +"	"+ to);
	}


}
