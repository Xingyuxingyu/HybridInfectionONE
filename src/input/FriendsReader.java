package input;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import core.DTNHost;
import core.NetworkInterface;
import core.SimError;

public class FriendsReader implements ExternalEventsReader {
	
	
	private Scanner scanner;
	
	public FriendsReader(File eventsFile){
		try {
			this.scanner = new Scanner(eventsFile);
		} catch (FileNotFoundException e) {
			throw new SimError(e.getMessage(),e);
		}
	}
	
	
	public List<Friend> readFriends(int hostAddress) {
		List<Friend> friends = new ArrayList<Friend>();
		// skip empty and comment lines
		Pattern skipPattern = Pattern.compile("(#.*)|(^\\s*$)");

		while (scanner.hasNextLine()) {
			String buff = scanner.nextLine();
		    String[] spliteStr = buff.split(" ");
		    int node1 = Integer.valueOf(spliteStr[0]);     
		    int node2 = Integer.valueOf(spliteStr[1]);                        
		    if(node1 == hostAddress){		    	
		    	friends.add(new Friend(hostAddress , node2));
		    }
		}
		return friends;
	}

	/**
	 * Parses a host address from a hostId string (the numeric part after
	 * optional non-numeric part).
	 * @param hostId The id to parse the address from
	 * @return The address
	 * @throws SimError if no address could be parsed from the id
	 */
	
	
	public void close() {
		this.scanner.close();
	}


	@Override
	public List<ExternalEvent> readEvents(int nrof) {
		// TODO Auto-generated method stub
		return null;
	}

}
