/*
 * @author Anupam and Gloria
 */
import java.util.ArrayList;

/*
*Added this dummy class as of now since I needed this class in the OptChoke class.  --Abhishek
*/

public class PeerInfo {
	private static int Peer_id;
	public static int unchokeInterval;
	public static int optimisticUnchokeInterval;	
	public static ArrayList <Integer>  chokedInterested;  //List of CHOKED neighbours who would be interested. 
	public static int optimisticUnchokedPeer;
	public static LoggerPeer log;
	public static OptUnchokeTimer optUnchokeTimer;
	
	public static ArrayList <Integer> Choked;
	public static ArrayList <Integer>  UnchokedTopK;
	
	public static void setPeerID(int id){
		Peer_id = id;
	}
	
	public static int getID(){
		return Peer_id;
	}
}
