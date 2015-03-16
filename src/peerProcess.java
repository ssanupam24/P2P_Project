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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.net.*;

public class peerProcess implements Runnable{
	//TODO: Add logger after every task
	
	private static int Peer_id;
	public static int unchokeInterval;
	public static int optimisticUnchokeInterval;
	//Add all the interested neighbors in the below variable if you have the complete file
	//Do the unchoke only for these neighbors and select it randomly
	public static ArrayList <NeighborInfo>  unchokedInterested;  //List of UNCHOKED neighbours who would be interested. 
	public static int optimisticUnchokedPeer;

	private static int peer_id;
	private BitField bitfield;
	private static LoggerPeer log;
	private HandleFile filePointer;
	private PeerConfigs peerConfigs;
	private NeighborInfo[] neighborInfo;
	private int totalNeighbors;
	ServerSocket uploadServerSocket;
	ServerSocket downloadServerSocket;
	ServerSocket haveServerSocket;
	boolean fullFile;
	
	public peerProcess(int peerID) throws IOException
	{
		peer_id = peerID;
		peerConfigs = new PeerConfigs();
		filePointer = new HandleFile(peer_id, peerConfigs);
		log = new LoggerPeer(peer_id);
		bitfield = new BitField(peerConfigs.getTotalPieces()); 
		neighborInfo = new NeighborInfo[peerConfigs.getTotalPeers()];
		optimisticUnchokeInterval = peerConfigs.getTimeOptUnchoke();
		unchokeInterval = peerConfigs.getTimeUnchoke();
		
	}
	
	public void run()
	{
		//Finished flag setup after checking the whole file value from bitfield
		for(int i = 0; i < peerConfigs.getTotalPeers(); i++){
			if((peerConfigs.getHasWholeFile(i)) && (peerConfigs.getPeerList(i) == peer_id)){
				fullFile = true;
			}
		}
		
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
			
			for(int j = 0; j < neighborInfo.length; j++){
				
				NeighborInfo rec = neighborInfo[j];
				//You don't have to submit a download and have thread for yourself
				if(rec.getPeerId() != peer_id) {
					Future<Object> downFuture = downloadPool
							.submit(new Download(peer_id, neighborInfo, rec,
									bitfield, filePointer, log));
					downList.add(downFuture);
					Future<Object> haveFuture = havePool
							.submit(new HaveMessage(peer_id, rec, log,
									neighborInfo));
					haveList.add(haveFuture);
				}
				
			}
			Future<Object> optFuture = optThread.submit(new OptUnchoke(peer_id, bitfield, neighborInfo, log, optimisticUnchokeInterval, filePointer));
			//Call unchoker function to start unchoker callable
			unchokerProcess();
			//Now check all the future objects and shut down threads if done with 
			//receiving and sending all the pieces
			optFuture.get();
			for(int j = 0; j < neighborInfo.length; j++){
				if(neighborInfo[j].getPeerId() != peer_id){
					downList.get(j).get();
					haveList.get(j).get();
				}
			}
			log.completeDownloadLog();
			downloadPool.shutdown();
			havePool.shutdown();
			optThread.shutdown();
			//Hopefully all the tasks are completed successfully and the control reaches here, 
			//now its celebration time :)
			for(int j = 0; j < neighborInfo.length; j++){
				//Close all the sockets
				if(neighborInfo[j].getPeerId() == peer_id) {
					uploadServerSocket.close();
					downloadServerSocket.close();
					haveServerSocket.close();
					
					neighborInfo[j].getHaveSocket().close();
					neighborInfo[j].getDownloadSocket().close();
					neighborInfo[j].getUploadSocket().close();	
				}
				else{
					neighborInfo[j].getHaveSocket().close();
					neighborInfo[j].getDownloadSocket().close();
					neighborInfo[j].getUploadSocket().close();
				}
			}
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		printNeighborInfo();	
	}
	//TODO: For Anupam>> Complete this ASAP and don't procrastinate 
	public void unchokerProcess() throws IOException, InterruptedException, ExecutionException{
		//This map will have all my preferred neighbors sorted in descending order according to download rates
		ArrayList<Integer> prefList = new ArrayList<Integer>();
		TreeMap prefNeighborList = new TreeMap(Collections.reverseOrder());
		ExecutorService uploadPool = Executors.newFixedThreadPool(peerConfigs.getPrefNeighbors());
		boolean finished;
		Random randomGenerator = new Random();
		int counter;
		int index;
		ArrayList<Future<Object>> uploadList = new ArrayList<Future<Object>>();
		while(true){
			finished = true;
			//Check whether all the peers have downloaded the entire file or not
			for(int i = 0; i < neighborInfo.length; i++){
				if(!neighborInfo[i].hasFinished())
					finished = false;
			}
			//if yes then break from the loop and return null
			if(finished)
				break;
			prefNeighborList.clear();
			prefList.clear();
			if(fullFile) {
				counter = 0;
				while(counter < peerConfigs.getPrefNeighbors()) {
					index = randomGenerator.nextInt(neighborInfo.length);
					if (neighborInfo[index].getPeerId() != peer_id
							&& neighborInfo[index].getBitField()
									.checkPiecesInterested(bitfield)) {
						prefList.add(index);
						counter++;
					}
				}
				for(int i = 0; i < prefList.size(); i++){
					Future<Object> uploadFuture = uploadPool.submit(new Unchoke(peer_id, neighborInfo[prefList.get(i)],log,
							neighborInfo, unchokeInterval, filePointer));
					uploadList.add(uploadFuture);
				}
				log.changeOfPreferredNeighbourLog(prefList);
				// Wait for the future objects here till the upload threads
				// complete their execution
				for (Future<Object> f : uploadList) {
					f.get();
				}
			}
			else {
				// Check all the download rate and select preferred neighbors
				for (int i = 0; i < neighborInfo.length; i++) {
					if (neighborInfo[i].getPeerId() != peer_id
							&& neighborInfo[i].getBitField()
									.checkPiecesInterested(bitfield)) {
						prefNeighborList.put(neighborInfo[i].getdownloadRate(),i);
					}
				}
				Set set = prefNeighborList.entrySet();
				Iterator it = set.iterator();
				// Start the threads for those neighbors
				counter = 0;
				while (it.hasNext()) {
					Map.Entry m = (Map.Entry) it.next();
					neighborInfo[(Integer) m.getValue()].resetDownload();
					if (counter >= peerConfigs.getPrefNeighbors())
						break;
					prefList.add(neighborInfo[(Integer) m.getValue()]
							.getPeerId());
					// Check if choked perhaps?
					Future<Object> uploadFuture = uploadPool
							.submit(new Unchoke(peer_id,
									neighborInfo[(Integer) m.getValue()], log,
									neighborInfo, unchokeInterval, filePointer));
					uploadList.add(uploadFuture);
					counter++;
				}
				log.changeOfPreferredNeighbourLog(prefList);
				// Wait for the future objects here till the upload threads
				// complete their execution
				for (Future<Object> f : uploadList) {
					f.get();
				}
			}
		}
		//Finally the pool is shutdown 
		uploadPool.shutdownNow();
	}
	
	public void printNeighborInfo()
	{
		for(int i = 0; i < totalNeighbors; ++i)
		{
			System.out.printf("Neighbor %d\nPeerID: %d\nStateOfChoke: %d", i,
					neighborInfo[i].getPeerId(), neighborInfo[i].getChokedByNeighborState());
		}
	}
	
	public void setupNeighborAndSelfInfo() throws Exception
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
		
		// Iterate through all peers in the peerConfigs object
		for(int i = 0; i < totalNeighbors; ++i)
		{
			currPeerID = peerConfigs.getPeerList(i);
			host = peerConfigs.getHostList(i);
			downloadPort = peerConfigs.getDownloadPortList(i);
			uploadPort = peerConfigs.getUploadPortList(i);
			havePort = peerConfigs.getHavePortList(i);
			
			// If a neighbor, populate neighbor information and set up client sockets
			if(currPeerID != peer_id)
			{	
				neighborInfo[i] = new NeighborInfo(totalPieces);
				neighborInfo[i].setPeerID(currPeerID);
				
				if(peerConfigs.getHasWholeFile(i))
					neighborInfo[i].getBitField().setAllBitsTrue();	
				
				setOthersInitialization(neighborInfo[i], i, peerIndex, host, downloadPort, uploadPort, havePort); // sets up client sockets
			}
			else // If self, set up BitField and server sockets
			{
				peerIndex = i;
				
				if(peerConfigs.getHasWholeFile(i))
					bitfield.setAllBitsTrue();
				
				neighborInfo[i] = new NeighborInfo(totalPieces);
				neighborInfo[i].setPeerID(peer_id);
				
				uploadServerSocket = new ServerSocket(uploadPort);
				downloadServerSocket = new ServerSocket(downloadPort);
				haveServerSocket = new ServerSocket(havePort);
				
				break;
			}
		}
		
		for(int i = peerIndex; i < totalNeighbors; ++i)
		{
			setSelfInitialization(uploadServerSocket, downloadServerSocket, haveServerSocket, i); 
		}
	}
	
	public void setSelfInitialization(ServerSocket uploadServerSocket, 
			ServerSocket downloadServerSocket, ServerSocket haveServerSocket, int index) throws Exception
	{
		// Sets up sockets to access the server sockets' I/O streams
		Socket uploadSocket = uploadServerSocket.accept();
		Socket downloadSocket = downloadServerSocket.accept();
		Socket haveSocket = haveServerSocket.accept();
	
		// Put the sockets in the neighborInfo object
		neighborInfo[index].setUploadSocket(uploadSocket);
		neighborInfo[index].setDownloadSocket(downloadSocket);
		neighborInfo[index].setHaveSocket(haveSocket);
		
		InputStream input = downloadSocket.getInputStream();
		OutputStream output = downloadSocket.getOutputStream();
		
		HandshakeMessage hs = new HandshakeMessage();	
		Message m = new Message();
		int currPeerID;
		
		// Receive handshake message
		hs.receiveMessage(downloadSocket);
		currPeerID = peerConfigs.getPeerList(index);
			
		if(!handshakeValid(hs, currPeerID))
		{
			throw new Exception("Error:  Invalid handshake message received from peer " + currPeerID + ".");
		}
			
		// Send handshake message in response
		hs.setPeerID(peer_id);
		hs.sendMessage(downloadSocket);

		// if a bitfield message is received, update the bitfield of the peer it
		// came from in the neighborInfo array
		m.receiveMessage(input);
		if (m.getType() == Message.bitfield) 
		{
			neighborInfo[index].setBitField(m.getPayload());

			// Send bitfield in response
			m.setType(Message.bitfield);
			m.setPayload(bitfield.changeBitToByteField());
			m.sendMessage(output);

			// Send an interested or notInterested message depending on the
			// received bitfield contents.
			if (bitfield.checkPiecesInterested(neighborInfo[index].getBitField())) {
				m.setType(Message.interested);
				m.setPayload(null);
				m.sendMessage(output);
			} else {
				m.setType(Message.notInterested);
				m.setPayload(null);
				m.sendMessage(output);
			}
		}
	}
	
	public void setOthersInitialization(NeighborInfo otherInfo, int neighborIndex, int peerIndex, String host, int downloadPort, int uploadPort, int havePort)throws UnknownHostException, IOException
	{
		int neighborID = otherInfo.getPeerId();
		InputStream input;
		OutputStream output;
		
		// set up client sockets for peer apart from local server peer
		Socket uploadClientSocket = new Socket(host, downloadPort);
		Socket downloadClientSocket = new Socket(host, uploadPort);
		Socket haveClientSocket = new Socket(host, havePort);
	
		// put client sockets in the neighborInfo array
		otherInfo.setDownloadSocket(downloadClientSocket);
		otherInfo.setUploadSocket(uploadClientSocket);
		otherInfo.setHaveSocket(haveClientSocket);

		output = uploadClientSocket.getOutputStream();
		input = uploadClientSocket.getInputStream();

		// If the neighbor is before the peer in the PeerInfo configuration file, 
		// the peer initiates the handshake process
		if(neighborIndex < peerIndex)
		{
			HandshakeMessage hs = new HandshakeMessage();
			hs.setPeerID(peer_id);
			hs.sendMessage(uploadClientSocket);
			
			// Receive handshake from neighber and check whether it is valid
			hs.receiveMessage(uploadClientSocket);
			if(!handshakeValid(hs, neighborID))
			{
				System.out.printf("\nError:  Peer %d received an invalid handshake message from peer %d\n", peer_id, hs.getPeerID());
				System.exit(1);
			}

			// Sent bitfield message
			Message m = new Message();
			m.setType(Message.bitfield);
			m.setPayload(bitfield.changeBitToByteField());
			m.sendMessage(output);
			
			// Receive bitfield
			m.receiveMessage(input);
			if(m.getType() == Message.bitfield)
			{
				otherInfo.setBitField(m.getPayload());
				
				// Send an interested or notInterested message depending on the received bitfield contents.
				if(bitfield.checkPiecesInterested(otherInfo.getBitField()))
				{
					m.setType(Message.interested);
					m.setPayload(null);
					m.sendMessage(output);
				}
				else
				{
					m.setType(Message.notInterested);
					m.setPayload(null);
					m.sendMessage(output);					
				}
			}
		}
	}

	public boolean handshakeValid(HandshakeMessage hs, int neighborID)
	{
		int peerID = hs.getPeerID();
		
		if(hs.getPeerID() == neighborID && hs.getHandshakeHeader().equals("P2PFILESHARINGPROJ"))
			return true;
		else
			return false;		
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
