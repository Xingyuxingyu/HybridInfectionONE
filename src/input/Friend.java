package input;


import core.DTNHost;
import core.NetworkInterface;
import core.World;


public class Friend {
	
		public static final String SETTING_FRIENDLISTPATH = "friends1.txt";
		public int toFriendAddress;
		public int fromFriendAddress;
		protected DTNHost toNode;
		protected NetworkInterface toInterface;
		protected DTNHost fromNode;
		protected NetworkInterface fromInterface;
		protected int friendLevel;
	
		public Friend(){
			this.friendLevel=0;
		}
		
		public Friend(int fromFriendAddress , int toFriendAddress){
			this.fromFriendAddress = fromFriendAddress;
			this.toFriendAddress = toFriendAddress;
			this.friendLevel=1;
		}
		
		public void setFriendHost( DTNHost fromHost , DTNHost toHost){
			this.fromNode = fromHost;
			this.toNode = toHost;
			this.friendLevel=1;
		}
		
		public void setFriendInterface( NetworkInterface fromInterface , NetworkInterface toInterface){
			this.fromInterface = fromInterface;
			this.toInterface = toInterface;
			this.friendLevel=1;
		}
		
		public DTNHost getFromHost(){
			return this.fromNode;
		}
		
		public DTNHost getToHost(){
			return this.toNode;
		}
		
		public NetworkInterface getToInterface(){
			return this.toInterface;
		}
		
		public NetworkInterface getFromInterface(){
			return this.fromInterface;
		}
           
		
       
           
           
}
