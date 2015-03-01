/*
 * @author Anupam and Gloria
 */
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

/*
*Added this dummy class as of now since I needed this class in the OptChoke class.  --Abhishek
*/

public class PeerInfo implements Runnable {

	protected static int unchokeInterval;
	protected static int optimisticUnchokeInterval;	
	public static ArrayList <Integer>  chokedInterested;  //List of neighbours who would be interested. (Used in OptChoke class)
	public static int optimisticUnchokedPeer;
	public static OptUnchokeTimer optUnchokeTimer;

	public static int peer_id;
	public BitField bitfield;
	public static LoggerPeer log;
	// public HandleFile filePointer;
	public PeerConfigs peerConfigs;
	public NeighborInfo[] neighborInfo;
	public int totalNeighbors;
	
	public PeerInfo(int peerID) throws FileNotFoundException
	{
		peer_id = peerID;
		peerConfigs = new PeerConfigs();
		
		// initialize handle file thing
		// initialize logger
		// initialize bitfield 
	}
	
	public void run() 
	{
		// do socket stuff here
		// loop through # peers
		setupNeighborInfoArray();
		setupTCPConnections();
		
		// during self init create server Socket & client Socket
		// set sockets to the function
		// do have & interested msg exhange in here in a while loop
		// setup TCP connection attempts in a while loop until connection successful
		// Loop through all peers
			// if hit your self, create server socket for yourself
		    // else for all other peers, create a client socket for yourself so you can connect to the other peers
		// Separate ExecutorService for download & upload & have
		// Handle exceptions for the tasks and restart the tasks, if possible
	}
	
	public void setupNeighborInfoArray()
	{
		totalNeighbors = peerConfigs.getTotalPeers() - 1;
		neighborInfo = new NeighborInfo[totalNeighbors];
		
		for(int i = 0; i < totalNeighbors; ++i)
		{
			// ensures the array is not populated with the current peer's information
			if(peerConfigs.peerList.get(i) != peer_id)
			{
				neighborInfo[i] = new NeighborInfo(peerConfigs.peerList.get(i));
				neighborInfo[i].stateOfChoke.set(1); // 1 means the neighbor starts out being choked
				neighborInfo[i].bitField = new BitField(peerConfigs.getTotalPieces());
				
				if(peerConfigs.hasWholeFile.get(i))
				{
					neighborInfo[i].bitField.setAllBitsTrue();
					neighborInfo[i].setDownloadAmount(peerConfigs.getTotalPieces());
				}	
				// initialize sockets.. not exactly sure how to.
				neighborInfo[i].uploadSocket = new Socket();
				// neighborInfo[i].downloadSocket = new Socket();
				neighborInfo[i].controlSocket = new Socket();		
			}

		}
	}
	
	public void setupTCPConnections()
	{
		int port = 1202;
		
		try 
		{
			Server server = new Server(port);
			Client client = new Client();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	public static void main(String [] args) throws NumberFormatException
	{	
		PeerInfo config = new PeerInfo(Integer.parseInt(args[0]));
		// create a thread object with PeerINfo obj as thread arg
		// start thread
	}
	
}
