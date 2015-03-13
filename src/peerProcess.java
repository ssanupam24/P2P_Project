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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.net.*;

public class peerProcess implements Runnable{
	//TODO: Add logger after every task
	
	private static int Peer_id;
	public static int unchokeInterval;
	public static int optimisticUnchokeInterval;	
	public static ArrayList <Integer>  chokedInterested;  //List of CHOKED neighbours who would be interested. 
	public static int optimisticUnchokedPeer;
	public static ArrayList <Integer> Choked;
	public static ArrayList <Integer>  UnchokedTopK;

	private static int peer_id;
	private BitField bitfield;
	private static LoggerPeer log;
	private HandleFile filePointer;
	private PeerConfigs peerConfigs;
	private NeighborInfo[] neighborInfo;
	private int totalNeighbors;
	
	public peerProcess(int peerID) throws IOException
	{
		peer_id = peerID;
		peerConfigs = new PeerConfigs();
		filePointer = new HandleFile(peer_id, peerConfigs);
		log = new LoggerPeer(peer_id);
		bitfield = new BitField(peerConfigs.getTotalPieces()); 
		optimisticUnchokeInterval = peerConfigs.getTimeOptUnchoke();
		unchokeInterval = peerConfigs.getTimeUnchoke();
	}
	
	public void run()
	{
		try {
			setupNeighborAndSelfInfo();
			//The doomsday thread starts now. Good Luck!!!
			ArrayList<Future<Object>> downList = new ArrayList<Future<Object>>();
			ArrayList<Future<Object>> haveList = new ArrayList<Future<Object>>();
			//Create executor services for download and have.
			ExecutorService downloadPool = Executors.newFixedThreadPool(totalNeighbors);
			ExecutorService havePool = Executors.newFixedThreadPool(totalNeighbors);
			//Create a fixed thread executor service for optunchoke
			ExecutorService optThread = Executors.newSingleThreadExecutor();
			
			for(int j = 0; j < totalNeighbors; j++){
				NeighborInfo rec = neighborInfo[j];
				Future<Object> downFuture = downloadPool.submit(new Download(peer_id, neighborInfo, rec, bitfield, filePointer, log));
				downList.add(downFuture);
				Future<Object> haveFuture = havePool.submit(new HaveMessage(peer_id, rec, log, neighborInfo));
				haveList.add(haveFuture);
				
			}
			Future<Object> optFuture = optThread.submit(new OptUnchoke(peer_id, bitfield, neighborInfo, log, optimisticUnchokeInterval, filePointer));
			//Call unchoker function to start unchoker callable
			unchokerProcess();
			//Now check all the future objects and shut down threads if done with 
			//receiving and sending all the pieces
			//TODO: Anupam>> Complete this ASAP
			//Hopefully all the tasks are completed successfully and the control reaches here, 
			//now its celebration time :)
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		printNeighborInfo();	
	}
	//TODO: Anupam>> Complete this ASAP
	public void unchokerProcess(){
		
	}
	
	public void printNeighborInfo()
	{
		for(int i = 0; i < totalNeighbors; ++i)
		{
			System.out.printf("Neighbor %d\nPeerID: %d\nStateOfChoke: %d", i,
					neighborInfo[i].getPeerId(), neighborInfo[i].getChokedByNeighborState());
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
		//Need to initialize the neighbor info array and totalNeighbors in the constructor
		int totalPeers = peerConfigs.getTotalPeers();
		totalNeighbors = totalPeers-1;
		neighborInfo = new NeighborInfo[totalPeers];
		
		// Iterate through all peers in the peerConfigs object
		for(int i = 0; i < totalPeers; ++i)
		{
			currPeerID = peerConfigs.getPeerList(i);
			host = peerConfigs.getHostList(i);
			downloadPort = peerConfigs.getDownloadPortList(i);
			uploadPort = peerConfigs.getUploadPortList(i);
			havePort = peerConfigs.getHavePortList(i);
			
			neighborInfo[i] = new NeighborInfo(currPeerID, totalPieces);
			
			if(peerConfigs.getHasWholeFile(i))
				neighborInfo[i].getBitField().setAllBitsTrue();	
			
			// If a neighbor, populate neighbor information and set up client sockets
			if(currPeerID != peer_id)
			{					
				setOthersInitialization(i, peerIndex, host, downloadPort, uploadPort, havePort); // sets up client sockets
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
	
	// left off here... need to fix handshaking/bitfield thing
	public void setSelfInitialization(ServerSocket uploadServerSocket, 
			ServerSocket downloadServerSocket, ServerSocket haveServerSocket, int index) throws IOException
	{
		// set up sockets to access the server sockets' I/O streams
		Socket uploadSocket = uploadServerSocket.accept();
		Socket downloadSocket = downloadServerSocket.accept();
		Socket haveSocket = haveServerSocket.accept();
	
		neighborInfo[index].setUploadSocket(uploadSocket);
		neighborInfo[index].setDownloadSocket(downloadSocket);
		neighborInfo[index].setControlSocket(haveSocket);
		
		Socket socket;
		InputStream input;
		OutputStream output;
		int numHandshakesExpected = peerConfigs.getTotalPeers() - index - 1;
		
		HandshakeMessage receivedHandshake = new HandshakeMessage();	
		Message m = new Message();
		
		int i = 0;
		while(i < numHandshakesExpected)
		{
			socket = neighborInfo[i].getUploadSocket();
			input = socket.getInputStream();
			output = socket.getOutputStream();
			
			receivedHandshake.receiveMessage(socket);
			
			if(handshakeValid(receivedHandshake, index))
			{
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
		
		// set up client sockets for peer apart from local server peer
		Socket uploadClientSocket = new Socket(host, downloadPort);
		Socket downloadClientSocket = new Socket(host, uploadPort);
		Socket haveClientSocket = new Socket(host, havePort);
	
		// put client sockets in the neighborInfo array
		neighborInfo[neighborIndex].setDownloadSocket(downloadClientSocket);
		neighborInfo[neighborIndex].setUploadSocket(uploadClientSocket);
		neighborInfo[neighborIndex].setControlSocket(haveClientSocket);

		input = uploadClientSocket.getInputStream();
		output = uploadClientSocket.getOutputStream();
		
		HandshakeMessage hs = new HandshakeMessage();
		HandshakeMessage receivedHandshake = new HandshakeMessage();
		Message m = new Message();
		boolean flag = true;
		
		byte[] bits;
		
		if(neighborIndex < peerIndex)
		{			
			hs.setPeerID(peerIndex);
			hs.sendMessage(uploadClientSocket);
			
			while(flag)
			{		
				receivedHandshake.receiveMessage(uploadClientSocket);
				//If handshaking fails for some reason, I guess this loop will continue execution
				//and we won't be able to start a new handshaking with any other peer.
				if(receivedHandshake.getPeerID() == neighborIndex)
				{
					m.setType(Message.bitfield);
					m.setPayload(neighborInfo[peerIndex].getBitField().changeBitToByteField());
					m.sendMessage(output);
					flag = false;
				}
			}	
			
			flag = true;
			//Again, same concern as mentioned above
			while(flag)
			{
				m.receiveMessage(input);
				if(m.getType() == Message.bitfield)
				{
					bits = m.getPayload();
					neighborInfo[neighborIndex].setBitField(bits);		
					flag = false;
				}
			}
		}
	}
	//You can verify from the peerId of the client peer after receiving the message.
	//This function is not necessary
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
		peerProcess config = new peerProcess(Integer.parseInt(args[0]));
        Thread t = new Thread(config);
        t.start();
	}
	
	public static int getPeerID(){
		return peer_id;
	}

}
