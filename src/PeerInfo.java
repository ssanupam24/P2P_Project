/*
 * @author Anupam and Gloria
 */
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
		printNeighborInfo();	
	}
	
	public void printNeighborInfo()
	{
		for(int i = 0; i < totalNeighbors; ++i)
		{
			System.out.printf("Neighbor %d\nPeerID: %d\nStateOfChoke: %d\nAmount of download: %d", i,
					neighborInfo[i].getPeerId(), neighborInfo[i].getChokedByNeighborState(), neighborInfo[i].getAmountOfDownload());
		}
	}
	
	public void setupNeighborAndSelfInfo() throws UnknownHostException, IOException
	{
		int currPeerID;
		int peerIndex = 0;  // index of the peer initializing the NeighborInfo array
		int totalPieces = peerConfigs.getTotalPieces();
		String host;
		int downloadPort;
		int uploadPort;
		int havePort;
		
		int totalPeers = peerConfigs.getTotalPeers();
		totalNeighbors = totalPeers - 1;
		int index = 0; // index of current neighbor for initialization purposes

		for(int i = 0; i < totalPeers; ++i)
			if(peerConfigs.getPeerList(i) == peer_id)
			{
				peerIndex = i;
				break;
			}
		
		neighborInfo = new NeighborInfo[totalNeighbors];
		
		// Iterate through all peers in the peerConfigs object
		for(int i = 0; i < totalPeers; ++i)
		{
			currPeerID = peerConfigs.getPeerList(i);
			host = peerConfigs.getHostList(i);
			downloadPort = peerConfigs.getDownloadPortList(i);
			uploadPort = peerConfigs.getUploadPortList(i);
			havePort = peerConfigs.getHavePortList(i);
			
			// If a neighbor, populate neighbor information and set up client sockets
			if(currPeerID != peer_id)
			{
				neighborInfo[index] = new NeighborInfo(currPeerID, totalPieces);
				
				if(peerConfigs.getHasWholeFile(i))
				{
					neighborInfo[index].getBitField().setAllBitsTrue();					
					neighborInfo[index].setDownloadAmount(totalPieces);
				}	
					
				setOthersInitialization(index, peerIndex, host, downloadPort, uploadPort, havePort); // sets up client sockets
				
				++index;
			}
			else // If self, set up BitField and server sockets
			{
				if(peerConfigs.getHasWholeFile(i))
					bitfield.setAllBitsTrue();
				
				ServerSocket uploadServerSocket = new ServerSocket(uploadPort);
				ServerSocket downloadServerSocket = new ServerSocket(downloadPort);
				ServerSocket haveServerSocket = new ServerSocket(havePort);
				
				setSelfInitialization(uploadServerSocket, downloadServerSocket, haveServerSocket, peerIndex); // sets up server sockets
			}
		}
	}
	
	public void setSelfInitialization(ServerSocket uploadServerSocket, 
			ServerSocket downloadServerSocket, ServerSocket haveServerSocket, int index) throws IOException
	{
		// set up sockets to access the server sockets' I/O streams
		Socket uploadSocket = uploadServerSocket.accept();
		Socket downloadSocket = downloadServerSocket.accept();
		Socket haveSocket = haveServerSocket.accept();
		
		Socket socket;
		InputStream input;
		OutputStream output;
		int numHandshakesToSend = index;
		
		// add code for handshaking
		
		for(int i = 0; i < index; ++i)
		{
			socket = neighborInfo[i].getUploadSocket();
			
			HandshakeMessage m = new HandshakeMessage();
			m.sendMessage(socket);
		}
		
		// need to simultaneously read for bitfields & handshake replies or is it fine to keep them separate??
		int i = 0;
		while(i < numHandshakesToSend)
		{
			socket = neighborInfo[i].getUploadSocket();
			input = socket.getInputStream();
			output = socket.getOutputStream();
			
			HandshakeMessage receivedHandshake = new HandshakeMessage();	
			receivedHandshake.receiveMessage(socket);
			
			if(handshakeValid(receivedHandshake, index))
			{
				Message m = new Message();
				m.setType(Message.bitfield);
				m.setPayload(bitfield.changeBitToByteField());
				m.sendMessage(output);
				
				++i;
			}
		}
	}
	
	public void setOthersInitialization(int neighborIndex, int peerIndex, String host, int downloadPort, int uploadPort, int havePort )throws UnknownHostException, IOException
	{
		InputStream input;
		OutputStream output;
		
		// set up client sockets for an other peer
		Socket uploadClientSocket = new Socket(host, downloadPort);
		neighborInfo[neighborIndex].setUploadSocket(uploadClientSocket);
	
		Socket downloadClientSocket = new Socket(host, uploadPort);
		neighborInfo[neighborIndex].setDownloadSocket(downloadClientSocket);
		
		Socket haveClientSocket = new Socket(host, havePort);
		neighborInfo[neighborIndex].setControlSocket(haveClientSocket);

		for(int i = 0; i < peerIndex; ++i)
		{			
			HandshakeMessage m = new HandshakeMessage();
			m.sendMessage(uploadClientSocket);
		}
		
		// need to simultaneously read for bitfields & handshake replies or is it fine to keep them separate??
		int i = 0;
		while(i < peerIndex)
		{
			input = uploadClientSocket.getInputStream();
			output = uploadClientSocket.getOutputStream();
			
			HandshakeMessage receivedHandshake = new HandshakeMessage();	
			receivedHandshake.receiveMessage(uploadClientSocket);
			
			if(handshakeValid(receivedHandshake, peerIndex))
			{
				Message m = new Message();
				m.setType(Message.bitfield);
				m.setPayload(bitfield.changeBitToByteField());
				m.sendMessage(output);
				
				++i;
			}
		}
		
		
		// do handshaking logic here
	}
	
	public boolean handshakeValid(HandshakeMessage hs, int peerIndex)
	{
		boolean valid = false;
		int peerId = hs.getPeerID();
		
		// see if the peerId is one of the neighbors before this peer in the list
		for(int i = 0; i < peerIndex; ++i)
			if(neighborInfo[i].getPeerId() == peerId)
				valid = true;
		
		if(!hs.getHandshakeHeader().equals("P2PFILESHARINGPROJ"))
			valid = false;
		
		return valid;
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
