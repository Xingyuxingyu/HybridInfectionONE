package input;

import java.util.ArrayList;
import java.util.List;

import core.Settings;
import core.SettingsError;

public class InfectionMessageGenerator extends MessageEventGenerator {
	private List<Integer> toIds;
	private int created = 0;
	private int from;
	
	
	public InfectionMessageGenerator(Settings s) {
		super(s);
		this.toIds = new ArrayList<Integer>();
		
		if (toHostRange == null) {
			throw new SettingsError("Destination host (" + TO_HOST_RANGE_S + 
					") must be defined");
		}
		for (int i = toHostRange[0]; i < toHostRange[1]; i++) {
			toIds.add(i);
		}
		from = drawHostAddress(hostRange);
	}

	
	
	public ExternalEvent nextEvent() {
		int responseSize = 0; /* no responses requested */
		int to;
		
		to = this.toIds.remove(0);
		
		if (to == from) { /* skip self */
			if (this.toIds.size() == 0) { /* oops, no more from addresses */
				this.nextEventsTime = Double.MAX_VALUE;
				return new ExternalEvent(Double.MAX_VALUE);
			} else {
				to = this.toIds.remove(0);
			}
		}

		if (this.toIds.size() == 0) {
			this.nextEventsTime = Double.MAX_VALUE; /* no messages left */
		} else {
			this.nextEventsTime += drawNextEventTimeDiff();
		}
				
		MessageCreateEvent mce = new MessageCreateEvent(from, to, getID(), 
				drawMessageSize(), responseSize, this.nextEventsTime);
		
		return mce;
	}

	

}
