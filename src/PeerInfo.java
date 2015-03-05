/*
 * @author Anupam and Gloria
 */
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.net.*;

/*
*Added this dummy class as of now since I needed this class in the OptChoke class.  --Abhishek
*/


public class PeerInfo implements Runnable{
	/*
	 * First section of class vars are part of the dummy class.  Need to figure out 
	 * what we need or don't need from this.
	 */
	private static int Peer_id;
	public static int unchokeInterval;
	public static int optimisticUnchokeInterval;	
	public static ArrayList <Integer>  chokedInterested;  //List of CHOKED neighbours who would be interested. 
	public static int optimisticUnchokedPeer;
	public static OptUnchokeTimer optUnchokeTimer;
	public static ArrayList <Integer> Choked;
	public static ArrayList <Integer>  UnchokedTopK;

	private static int peer_id;
	private BitField bitfield;
	private static LoggerPeer log;
	private HandleFile filePointer;
	private PeerConfigs peerConfigs;
	private NeighborInfo[] neighborInfo;
	private int totalNeighbors;
	private ServerSocket U;
	private ServerSocket D;
	private ServerSocket H;
	private Socket u;
	private Socket d;
	private Socket h;
	
	public PeerInfo(int peerID) throws IOException
	{
		peer_id = peerID;
		peerConfigs = new PeerConfigs();
		filePointer = new HandleFile(peer_id, peerConfigs);
		log = new LoggerPeer(peer_id);
		bitfield = new BitField(peerConfigs.getTotalPieces()); 
	}
	
	public void run()
	{
		try {
			setupNeighborAndSelfInfo();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void setupNeighborAndSelfInfo() throws UnknownHostException, IOException
	{
		int currPeerID;
		int peerIndex;  // index of the peer initializing the NeighborInfo array
		int totalPieces = peerConfigs.getTotalPieces();
		String host;
		int port;
		
		int totalPeers = peerConfigs.getTotalPeers();
		totalNeighbors = totalPeers - 1;
		int index = 0; // index of current neighbor for initialization purposes

		neighborInfo = new NeighborInfo[totalNeighbors];
		
		// Iterate through all peers in the peerConfigs object
		for(int i = 0; i < totalPeers; ++i)
		{
			currPeerID = peerConfigs.getPeerList(i);
			host = peerConfigs.getHostList(i);
			port = peerConfigs.getUploadPortList(i);
			
			// If a neighbor, populate neighbor information and set up client sockets
			if(currPeerID != peer_id)
			{
				neighborInfo[index] = new NeighborInfo(currPeerID, totalPieces);
				
				if(peerConfigs.getHasWholeFile(i))
				{
					neighborInfo[index].getBitField().setAllBitsTrue();					
					neighborInfo[index].setDownloadAmount(totalPieces);
				}	
				
				setOthersInitialization(index, host, port); // sets up client sockets
				
				++index;
			}
			else // If self, set up BitField and server sockets
			{
				if(peerConfigs.getHasWholeFile(i))
					bitfield.setAllBitsTrue();
				
				peerIndex = i;
				setSelfInitialization(peerIndex); // sets up server sockets
			}
		}
	}
	
	public void setSelfInitialization(int index) throws IOException
	{
		int port = peerConfigs.getDownloadPortList(index);
		
		// Set up server sockets
		U = new ServerSocket(port);
		D = new ServerSocket(port);
		H = new ServerSocket(port);
		
		// set up sockets to access the server sockets' I/O streams
		u = U.accept();
		d = D.accept();
		h = D.accept();
	}
	
	public void setOthersInitialization(int index, String host, int port) throws UnknownHostException, IOException
	{
		// set up client sockets for an other peer
		Socket u = new Socket(host, port);
		neighborInfo[index].setUploadSocket(u);
		
		Socket d = new Socket(host, port);
		neighborInfo[index].setDownloadSocket(d);
		
		Socket h = new Socket(host, port);
		neighborInfo[index].setControlSocket(h);
	}
	
	public static void main(String [] args) throws NumberFormatException, IOException
	{	
		// Create PeerInfo object and start it as a separate thread
		PeerInfo config = new PeerInfo(Integer.parseInt(args[0]));
        Thread t = new Thread(config);
        t.start();
	}
	
	public static int getPeerID(){
		return peer_id;
	}

}
