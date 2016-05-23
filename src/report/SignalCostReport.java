package report;

import core.SignalListener;

public class SignalCostReport extends Report implements SignalListener{
	
	public static String HEADER = "# signal cost ";
	
	public SignalCostReport() {
		init();
	}

	@Override
	public void init() {
		super.init();
		write(HEADER);
	}
	
	@Override
	public void updated(int newcost) {
		// TODO Auto-generated method stub
		
	}
	
	
	@Override
	public void done() {
		super.done();
	}
}
