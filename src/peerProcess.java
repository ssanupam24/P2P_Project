/*
 * @author Anupam and Gloria
 * This class is the main class that fires up a peer process and starts the protocol
 * It spawns different threads for different tasks and takes care of the unchoke process as well
 */
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.net.*;

public class peerProcess implements Runnable{

	public final int unchokeInterval;
	public final int optimisticUnchokeInterval;
	private final int peer_id;
	private BitField bitfield;
	private static LoggerPeer log;
	private HandleFile filePointer;
	private PeerConfigs peerConfigs;
	private NeighborInfo[] neighborInfo;
	private int totalNeighbors;
	private int noOfPeersToUpload;
	ServerSocket uploadServerSocket;
	ServerSocket downloadServerSocket;
	ServerSocket haveServerSocket;
	boolean fullFile;
	private ExecutorService downloadPool;
	private ExecutorService havePool;
	private ExecutorService optThread;
	public peerProcess(int peerID) throws Exception
	{
		this.peer_id = peerID;
		this.peerConfigs = new PeerConfigs();
		this.filePointer = new HandleFile(peer_id, peerConfigs);
		log = new LoggerPeer(peer_id);
		this.bitfield = new BitField(peerConfigs.getTotalPieces()); 
		this.totalNeighbors = peerConfigs.getTotalPeers() - 1;
		this.neighborInfo = new NeighborInfo[totalNeighbors];
		this.optimisticUnchokeInterval = peerConfigs.getTimeOptUnchoke();
		this.unchokeInterval = peerConfigs.getTimeUnchoke();
		this.noOfPeersToUpload = peerConfigs.getTotalPeers() - peerConfigs.getTotalPeersWithEntireFile();
		
	}
	/*
	 * This function starts the peer to peer protocol and it is run for each peer
	 */
	public void run()
	{
		System.out.println("peerProcess Started");
		//Finished flag setup after checking the whole file value from bitfield
		for(int i = 0; i < peerConfigs.getTotalPeers(); i++){
			if((peerConfigs.getHasWholeFile(i)) && (peerConfigs.getPeerList(i) == peer_id)){
				fullFile = true;
				bitfield.setAllBitsTrue();
				break;
			}
		}
		try {
			setupNeighborAndSelfInfo();

			Vector<Future<Object>> downList = new Vector<Future<Object>>();
			Vector<Future<Object>> haveList = new Vector<Future<Object>>();
			
			//Create executor services for download and have.
			downloadPool = Executors.newFixedThreadPool(totalNeighbors);
			havePool = Executors.newFixedThreadPool(totalNeighbors);
			
			//Create a fixed thread executor service for optunchoke
			optThread = Executors.newSingleThreadExecutor();
			
			if(!fullFile){
				for(int j = 0; j < neighborInfo.length; j++){
					
					NeighborInfo rec = neighborInfo[j];
					Future<Object> downFuture = downloadPool.submit(new Download(peer_id, neighborInfo, rec,
									bitfield, filePointer, log));
					downList.add(downFuture);
					Future<Object> haveFuture = havePool.submit(new HaveMessage(peer_id, rec, log,
									neighborInfo, bitfield, fullFile, noOfPeersToUpload));
					haveList.add(haveFuture);
				}
			}
			else{
				for(int j = 0; j < neighborInfo.length; j++){
					
					NeighborInfo rec = neighborInfo[j];
					Future<Object> haveFuture = havePool.submit(new HaveMessage(peer_id, rec, log,
										neighborInfo, bitfield, fullFile, noOfPeersToUpload));
					haveList.add(haveFuture);
				}
			}
			Future<Object> optFuture = optThread.submit(new OptUnchoke(peer_id, bitfield, neighborInfo, log, optimisticUnchokeInterval, filePointer, fullFile, noOfPeersToUpload));
			//Call unchokerProcess function to start unchoker callable
			unchokerProcess();
			//Now check all the future objects and shut down threads if done with 
			//receiving and sending all the pieces
			optFuture.get();
			optThread.shutdown();
			if(!fullFile) {
				for(int j = 0; j < downList.size(); j++){
					
					downList.get(j).get();
				}
				downloadPool.shutdown();
				for(int j = 0; j < haveList.size(); j++){
					haveList.get(j).get();
				}
				havePool.shutdown();
			}
			else{
				for(int j = 0; j < haveList.size(); j++){
					
					haveList.get(j).get();
				}
				havePool.shutdown();
			}

			uploadServerSocket.close();
			downloadServerSocket.close();
			haveServerSocket.close();
			for(int j = 0; j < neighborInfo.length; j++){
				// Close all the sockets
				neighborInfo[j].getHaveSocket().close();
				neighborInfo[j].getUploadSocket().close();
			}
			filePointer.getFile().close();
			log.close();
			System.exit(0);
		} 
		catch (Exception e) {
			try{
			
			downloadPool.shutdown();
			havePool.shutdown();
			optThread.shutdown();
			uploadServerSocket.close();
			downloadServerSocket.close();
			haveServerSocket.close();
			for(int j = 0; j < neighborInfo.length; j++){
				// Close all the sockets
				neighborInfo[j].getHaveSocket().close();
				neighborInfo[j].getUploadSocket().close();
				}
			filePointer.getFile().close();
			log.close();
			System.exit(0);
			}
			catch(Exception e1){
				System.exit(0);
			}
		}	
	}
	/*
	 * This function takes care of the unchoker process. It selects preferred neighbors and spawns thread for each preferred neighbors.
	 */
	public synchronized void unchokerProcess() throws Exception{
		//This map will have all my preferred neighbors sorted in descending order according to download rates
		
		Vector<Integer> prefList = new Vector<Integer>();
		Vector<Integer> prefNeighborList = new Vector<Integer>(); // for peers with the whole file
		TreeMap<Integer, Vector<Integer>> prefNeighborList1 = new TreeMap<Integer, Vector<Integer>>(Collections.reverseOrder()); // for peers without the whole file
		ExecutorService uploadPool = Executors.newFixedThreadPool(peerConfigs.getPrefNeighbors());
		boolean finished;
		Random randomGenerator = new Random();
		int counter;
		int counter1 = 0;
		int index;
		Vector<Future<Integer>> uploadList = new Vector<Future<Integer>>();
		
		while(true){
			finished = false;
			counter = 0;
			for(int i = 0; i < neighborInfo.length; i++){
				if(neighborInfo[i].getDoneUpload().get() == 1) {
					counter++;
				}
			}
			
			if(fullFile) {
				if(counter == noOfPeersToUpload){
					finished = true;
				}
			}
			else{
				if(counter == (noOfPeersToUpload - 1)) {
					finished = true;
				}
			}
			if(finished)
				break;
			counter = 0;
			try {
			
			prefNeighborList.clear();
			prefNeighborList1.clear();
			prefList.clear();
			uploadList.clear();
			
			//This logic is for those peers that have the full file.
			if(fullFile) {
				counter = 0;
				for(int k = 0; k < neighborInfo.length && counter < peerConfigs.getPrefNeighbors(); k++){
					if((neighborInfo[k].getBitField().checkPiecesInterested(bitfield)) && 
							(neighborInfo[k].getNeighborChokedState().get() == 0) && (neighborInfo[k].getDoneUpload().get() == 0)){
						counter++;
					}
				}
				
				while(counter != 0) {
					index = randomGenerator.nextInt(neighborInfo.length);
					while(prefNeighborList.contains(index) || (!neighborInfo[index].getBitField().checkPiecesInterested(bitfield))
							|| (neighborInfo[index].getNeighborChokedState().get() != 0) || (neighborInfo[index].getDoneUpload().get() != 0)) {
						index = randomGenerator.nextInt(neighborInfo.length);
					}
					prefNeighborList.add(index);
					counter--;
				}
				
				while (prefNeighborList.size() != 0) {
					prefList.add(neighborInfo[prefNeighborList.get(0)].getPeerId());
					Future<Integer> uploadFuture = uploadPool.submit(new Unchoke(peer_id, neighborInfo[prefNeighborList.get(0)],log,
							neighborInfo, unchokeInterval, filePointer));
					uploadList.add(uploadFuture);
					prefNeighborList.remove(0);
				}
				if(prefList.size() != 0)
					log.changeOfPreferredNeighbourLog(prefList);
				// Wait for the future objects here till the upload threads complete their execution
				if(uploadList.size() != 0) {
					for (Future<Integer> f : uploadList) {
						f.get();
					}
				}
			}
			else {
				// Check all the download rate and select preferred neighbors
				for (int i = 0; i < neighborInfo.length; i++) {
					if ((neighborInfo[i].getBitField().checkPiecesInterested(bitfield))&& 
							(neighborInfo[i].getNeighborChokedState().get() == 0) && (neighborInfo[i].getDoneUpload().get() == 0)) {
						if(prefNeighborList1.containsKey(neighborInfo[i].getdownloadRate())){
							prefNeighborList1.get(neighborInfo[i].getdownloadRate()).add(i);
						}
						else{
							Vector<Integer> v = new Vector<Integer>();
							v.add(i);
							prefNeighborList1.put(neighborInfo[i].getdownloadRate(),v);
						}
					}
				}
				Set<Entry<Integer, Vector<Integer>>> set = prefNeighborList1.entrySet();
				Iterator<Entry<Integer, Vector<Integer>>> it = set.iterator();
				// Start the threads for those neighbors
				counter = 0;
				while (it.hasNext()) {
					Map.Entry<Integer, Vector<Integer>> m = (Map.Entry<Integer, Vector<Integer>>) it.next();
					Vector<Integer> v = m.getValue();
					while(v.size() > 0){
						index = randomGenerator.nextInt(v.size());
						prefList.add(neighborInfo[v.get(index)].getPeerId());
						neighborInfo[v.get(index)].resetDownload();
						Future<Integer> uploadFuture = uploadPool.submit(new Unchoke(peer_id,
								neighborInfo[v.get(index)], log,
								neighborInfo, unchokeInterval, filePointer));
						uploadList.add(uploadFuture);
						v.remove(index);
						counter++;
						if (counter >= peerConfigs.getPrefNeighbors())
							break;
					}
					if (counter >= peerConfigs.getPrefNeighbors())
						break;
				}
				
				if(prefList.size() != 0)
					log.changeOfPreferredNeighbourLog(prefList);
				// Wait for the future objects here till the upload threads complete their execution
				if(uploadList.size() != 0){
					for (Future<Integer> f : uploadList) {
						f.get();
					}
				}
			}
			//Shut down the pool 
			uploadPool.shutdownNow();
			uploadPool = Executors.newFixedThreadPool(peerConfigs.getPrefNeighbors());
		}
		catch(Exception e){
			counter1++;
			if(fullFile) {
				if(counter1 == noOfPeersToUpload)
					break;
			}
			else{
				if(counter1 == (noOfPeersToUpload - 1))
					break;
			}
		}
	}
		
		//Shut down the pool  
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
	/*
	 * This function sets up the client and server sockets and also sends/receives handshake/bitfield messages
	 */
	public synchronized void setupNeighborAndSelfInfo() throws Exception
	{
		int currPeerID;
		int peerIndex = 0;  // index of the peer initializing the NeighborInfo array
		int totalPieces = peerConfigs.getTotalPieces();
		String host;
		int downloadPort;
		int uploadPort;
		int havePort;
		// Iterate through all peers in the peerConfigs object
		for(int i = 0; i < peerConfigs.getTotalPeers(); ++i)
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
				
				setOthersInitialization(neighborInfo[i], host, downloadPort, uploadPort, havePort); // sets up client sockets
			}
			else // If self, set up BitField and server sockets
			{
				peerIndex = i;
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
	/*
	 * This function is for setting up the server sockets and accepting connections from clients
	 */
	public synchronized void setSelfInitialization(ServerSocket uploadServerSocket, 
			ServerSocket downloadServerSocket, ServerSocket haveServerSocket, int index) throws Exception
	{
		
		neighborInfo[index] = new NeighborInfo(peerConfigs.getTotalPieces());
		
		// Sets up sockets to access the server sockets' I/O streams 
		Socket downloadSocket = downloadServerSocket.accept();
		Socket uploadSocket = uploadServerSocket.accept();
		Socket haveSocket = haveServerSocket.accept();
		downloadSocket.setSoTimeout((peerConfigs.getTimeOptUnchoke() > peerConfigs.getTimeUnchoke() ? peerConfigs.getTimeOptUnchoke() : peerConfigs.getTimeUnchoke())*2000);
		uploadSocket.setSoTimeout((peerConfigs.getTimeOptUnchoke() > peerConfigs.getTimeUnchoke() ? peerConfigs.getTimeOptUnchoke() : peerConfigs.getTimeUnchoke())*2000);
		haveSocket.setSoTimeout((peerConfigs.getTimeOptUnchoke() > peerConfigs.getTimeUnchoke() ? peerConfigs.getTimeOptUnchoke() : peerConfigs.getTimeUnchoke())*2000);
		// Put the sockets in the neighborInfo object
		neighborInfo[index].setUploadSocket(uploadSocket);
		neighborInfo[index].setDownloadSocket(downloadSocket);
		neighborInfo[index].setHaveSocket(haveSocket);
		
		InputStream input = downloadSocket.getInputStream();
		OutputStream output = downloadSocket.getOutputStream();
		
		HandshakeMessage hs = new HandshakeMessage();
		int currPeerID;
		//Check if the peer ID is the correct one since we are iterating from the server peer and not client peer
		// Receive handshake message
		hs.receiveMessage(downloadSocket);
		currPeerID = peerConfigs.getPeerList(index+1);
		int hsId = hs.getPeerID();	
		if(!handshakeValid(hs, currPeerID))
			throw new Exception("Error:  Peer " + peer_id + " received an invalid handshake message from peer " + hs.getPeerID() + "."); 
			
		log.tcpConnectedLog(hs.getPeerID());
		neighborInfo[index].setPeerID(hsId);
		// Send handshake message in response
		hs.setPeerID(peer_id);
		hs.sendMessage(downloadSocket);

		// if a bitfield message is received, update the bitfield of the peer it
		// came from in the neighborInfo array
		Message m = new Message();
		m.receiveMessage(input);
		if (m.getType() == Message.bitfield) 
		{
			BitField bf = new BitField(peerConfigs.getTotalPieces());
			bf.setBitFromByte(m.getPayload());
			neighborInfo[index].setBitField(bf);
			
			// Send bitfield in response
			Message m1 = new Message();
			m1.setType(Message.bitfield);
			m1.setPayload(bitfield.changeBitToByteField());
			m1.sendMessage(output);
		}
		Message m2 = new Message();
		m2.receiveMessage(input);
		if(m2.getType() == Message.notInterested){
			log.notInterestedLog(hsId);
		}
		else if(m2.getType() == Message.interested){
			log.interestedLog(hsId);
		}
		// Send an interested or notInterested message depending on the
		// received bitfield contents.
		Message m3 = new Message();
		if (bitfield.checkPiecesInterested(neighborInfo[index].getBitField())) {
			m3.setType(Message.interested);
			m3.setPayload(null);
			m3.sendMessage(output);
		} else {
			m3.setType(Message.notInterested);
			m3.setPayload(null);
			m3.sendMessage(output);
		}
	}
	/*
	 * This function is used to set up the client sockets and make connections with the servers
	 */
	public synchronized void setOthersInitialization(NeighborInfo otherInfo, String host, int downloadPort, int uploadPort, int havePort)throws Exception
	{
		int neighborID = otherInfo.getPeerId();
		InputStream input;
		OutputStream output;
		
		// set up client sockets for peer apart from local server peer
		Socket uploadClientSocket = new Socket(host, downloadPort);
		Socket downloadClientSocket = new Socket(host, uploadPort);
		Socket haveClientSocket = new Socket(host, havePort);
		uploadClientSocket.setSoTimeout((peerConfigs.getTimeOptUnchoke() > peerConfigs.getTimeUnchoke() ? peerConfigs.getTimeOptUnchoke() : peerConfigs.getTimeUnchoke())*2000);
		downloadClientSocket.setSoTimeout((peerConfigs.getTimeOptUnchoke() > peerConfigs.getTimeUnchoke() ? peerConfigs.getTimeOptUnchoke() : peerConfigs.getTimeUnchoke())*2000);
		haveClientSocket.setSoTimeout((peerConfigs.getTimeOptUnchoke() > peerConfigs.getTimeUnchoke() ? peerConfigs.getTimeOptUnchoke() : peerConfigs.getTimeUnchoke())*2000);
		// put client sockets in the neighborInfo array
		otherInfo.setDownloadSocket(downloadClientSocket);
		otherInfo.setUploadSocket(uploadClientSocket);
		otherInfo.setHaveSocket(haveClientSocket);

		output = uploadClientSocket.getOutputStream();
		input = uploadClientSocket.getInputStream();

		// If the neighbor is before the peer in the PeerInfo configuration file, 
		// the peer initiates the handshake process
		HandshakeMessage hs = new HandshakeMessage();
		hs.setPeerID(peer_id);
		hs.sendMessage(uploadClientSocket);
		
		// Receive handshake from neighbor and check whether it is valid
		hs.receiveMessage(uploadClientSocket);
		log.tcpConnectionEstablishedLog(otherInfo.getPeerId());
		if (!handshakeValid(hs, neighborID))
			throw new Exception("Error:  Peer " + peer_id + " received an invalid handshake message from peer " + hs.getPeerID() + " .");

		// Send bitfield message
		Message m = new Message();
		m.setType(Message.bitfield);
		m.setPayload(bitfield.changeBitToByteField());
		m.sendMessage(output);

		// Receive bitfield
		Message m3 = new Message();
		m3.receiveMessage(input);
		if (m3.getType() == Message.bitfield) {
			BitField bf = new BitField(peerConfigs.getTotalPieces());
			bf.setBitFromByte(m3.getPayload());
			otherInfo.setBitField(bf);			
		}
		// Send an interested or notInterested message depending on the
		// received bitfield contents.
		Message m1 = new Message();
		if (bitfield.checkPiecesInterested(otherInfo.getBitField())) {
			m1.setType(Message.interested);
			m1.setPayload(null);
			m1.sendMessage(output);
		} else {
			m1.setType(Message.notInterested);
			m1.setPayload(null);
			m1.sendMessage(output);
		}
		//Receive interested/not interested messages
		Message m2 = new Message();
		m2.receiveMessage(input);
		if(m2.getType() == Message.notInterested){
			log.notInterestedLog(otherInfo.getPeerId());
		}
		else if(m2.getType() == Message.interested){
			log.interestedLog(otherInfo.getPeerId());
		}
	}
	/*
	 * This function validates the handshake message received by the peers
	 */
	public synchronized boolean handshakeValid(HandshakeMessage hs, int neighborID)
	{
		
		if(hs.getPeerID() == neighborID && hs.getHandshakeHeader().equals("P2PFILESHARINGPROJ"))
			return true;
		else
			return false;		
	}
	
	public static void main(String [] args) throws Exception
	{	
		// Create PeerInfo object and start it as a separate thread
		peerProcess config = new peerProcess(Integer.parseInt(args[0]));
        Thread t = new Thread(config);
        t.start();
	}
	
	public int getPeerID(){
		return peer_id;
	}

}
