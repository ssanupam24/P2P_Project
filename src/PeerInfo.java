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
		System.out.println("Wooga!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		try {
			setupNeighborAndSelfInfo();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
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
	
	public void setupNeighborAndSelfInfo() throws UnknownHostException, IOException
	{
		int currPeerID;
		int peerIndex;  // index of the peer initializing the NeighborInfo array
		int totalPieces = peerConfigs.getTotalPieces();
		String host;
		int port;
		
		int totalPeers = peerConfigs.getTotalPeers();
		totalNeighbors = totalPeers - 1;
		int index = 0; // index of current neighbor for intialization purposes

		neighborInfo = new NeighborInfo[totalNeighbors];
		
		for(int i = 0; i < totalPeers; ++i)
		{
			currPeerID = peerConfigs.getPeerList(i);
			host = peerConfigs.getHostList(i);
			port = peerConfigs.getUploadPortList(i);
			
			// ensures the array is not populated with the current peer's information
			if(currPeerID != peer_id)
			{
				neighborInfo[index] = new NeighborInfo(currPeerID, totalPieces);
				
				if(peerConfigs.getHasWholeFile(i))
				{
					neighborInfo[index].getBitField().setAllBitsTrue();					
					neighborInfo[index].setDownloadAmount(totalPieces);
				}	
				
				setOthersInitialization(index, host, port);
				
				++index;
			}
			else
			{
				if(peerConfigs.getHasWholeFile(i))
					bitfield.setAllBitsTrue();
				
				peerIndex = i;
				setSelfInitialization(peerIndex);
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
		PeerInfo config = new PeerInfo(Integer.parseInt(args[0]));
		config.run();
		// create a thread object with PeerINfo obj as thread arg
		// start thread
	}
	
	
	public static int getPeerID(){
		return peer_id;
	}

}
