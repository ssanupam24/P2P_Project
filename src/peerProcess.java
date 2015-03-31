/*
 * @author Anupam and Gloria
 */
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.net.*;

public class peerProcess implements Runnable{

	public final int unchokeInterval;
	public final int optimisticUnchokeInterval;
	//Add all the interested neighbors in the below variable if you have the complete file
	//Do the unchoke only for these neighbors and select it randomly

	private final int peer_id;
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
		
	}
	
	public void run()
	{
		//Finished flag setup after checking the whole file value from bitfield
		for(int i = 0; i < peerConfigs.getTotalPeers(); i++){
			if((peerConfigs.getHasWholeFile(i)) && (peerConfigs.getPeerList(i) == peer_id)){
				fullFile = true;
				bitfield.setAllBitsTrue();
				System.out.println("Inside checking file and setting up bitfield");
				break;
			}
		}
		try {
			setupNeighborAndSelfInfo();
			//The doomsday thread starts now. Good Luck!!!
			System.out.println("Thread spawning starts");
			Vector<Future<Object>> downList = new Vector<Future<Object>>();
			Vector<Future<Object>> haveList = new Vector<Future<Object>>();
			//Create executor services for download and have.
			ExecutorService downloadPool = Executors.newFixedThreadPool(totalNeighbors);
			ExecutorService havePool = Executors.newFixedThreadPool(totalNeighbors);
			//Create a fixed thread executor service for optunchoke
			ExecutorService optThread = Executors.newSingleThreadExecutor();
			if(!fullFile){
				for(int j = 0; j < neighborInfo.length; j++){
					System.out.println("Submitted download and have");
					NeighborInfo rec = neighborInfo[j];
					Future<Object> downFuture = downloadPool.submit(new Download(peer_id, neighborInfo, rec,
									bitfield, filePointer, log));
					downList.add(downFuture);
					Future<Object> haveFuture = havePool.submit(new HaveMessage(peer_id, rec, log,
									neighborInfo, bitfield));
					haveList.add(haveFuture);
				
				}
			}
			else{
				for(int j = 0; j < neighborInfo.length; j++){
					System.out.println("Submitted have");
					NeighborInfo rec = neighborInfo[j];
						Future<Object> haveFuture = havePool.submit(new HaveMessage(peer_id, rec, log,
										neighborInfo, bitfield));
						haveList.add(haveFuture);
					
				}
			}
			Future<Object> optFuture = optThread.submit(new OptUnchoke(peer_id, bitfield, neighborInfo, log, optimisticUnchokeInterval, filePointer));
			//Call unchoker function to start unchoker callable
			unchokerProcess();
			//Now check all the future objects and shut down threads if done with 
			//receiving and sending all the pieces
			optFuture.get();
			if(!fullFile) {
				for(int j = 0; j < neighborInfo.length; j++){
					System.out.println("Waiting for return in get");
					downList.get(j).get();
					haveList.get(j).get();
				}
			}
			else{
				for(int j = 0; j < neighborInfo.length; j++){
					System.out.println("Waiting for return in get");
					haveList.get(j).get();
				}
			}
			log.completeDownloadLog();
			downloadPool.shutdown();
			havePool.shutdown();
			optThread.shutdown();
			//Hopefully all the tasks are completed successfully and the control reaches here, 
			//now its celebration time :)
			uploadServerSocket.close();
			downloadServerSocket.close();
			haveServerSocket.close();
			for(int j = 0; j < neighborInfo.length; j++){
				// Close all the sockets
				neighborInfo[j].getHaveSocket().close();
				neighborInfo[j].getDownloadSocket().close();
				neighborInfo[j].getUploadSocket().close();
			}
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		//printNeighborInfo();	
	}
	// Need to add flag if you have the entire file to skip the download and have callable thread
	public synchronized void unchokerProcess() throws IOException, InterruptedException, ExecutionException{
		//This map will have all my preferred neighbors sorted in descending order according to download rates
		System.out.println("Uploading process starts");
		Vector<Integer> prefList = new Vector<Integer>();
		TreeMap<Integer, Integer> prefNeighborList = new TreeMap<Integer, Integer>(Collections.reverseOrder());
		ExecutorService uploadPool = Executors.newFixedThreadPool(peerConfigs.getPrefNeighbors());
		boolean finished;
		Random randomGenerator = new Random();
		int counter;
		int index;
		Vector<Future<Object>> uploadList = new Vector<Future<Object>>();
		Message m1 = new Message();
		while(true){
			finished = true;
			//Check whether all the peers have downloaded the entire file or not
			for(int i = 0; i < neighborInfo.length; i++){
				if (neighborInfo[i].getNeighborChokedState().compareAndSet(1, 0)) {
					m1.setType(Message.choke);
					m1.setPayload(null);
					m1.sendMessage(neighborInfo[i].getUploadSocket()
							.getOutputStream());
				}
				if (!neighborInfo[i].hasFinished())
					finished = false;
			}
			//if yes then break from the loop and return null
			if(finished)
				break;
			prefNeighborList.clear();
			prefList.clear();
			if(fullFile) {
				System.out.println("Choose pref neighbors");
				counter = 0;
				for(int k = 0; k < neighborInfo.length && counter < peerConfigs.getPrefNeighbors(); k++){
					if(neighborInfo[k].getBitField().checkPiecesInterested(bitfield)){
						counter++;
					}
				}
				while(counter != 0) {
					index = randomGenerator.nextInt(neighborInfo.length);
					if ((neighborInfo[index].getBitField().checkPiecesInterested(bitfield))) {
						prefNeighborList.put(neighborInfo[index].getPeerId(),index);
						counter--;
					}
				}
				System.out.println("Before future for loop");
				Set set = prefNeighborList.entrySet();
				Iterator it = set.iterator();
				while (it.hasNext()) {
					Map.Entry<Integer, Integer> m = (Map.Entry) it.next();
					prefList.add((Integer)m.getKey());
					Future<Object> uploadFuture = uploadPool.submit(new Unchoke(peer_id, neighborInfo[(Integer) m.getValue()],log,
							neighborInfo, unchokeInterval, filePointer));
					uploadList.add(uploadFuture);
				}
				System.out.println("After future for loop");
				log.changeOfPreferredNeighbourLog(prefList);
				// Wait for the future objects here till the upload threads
				// complete their execution
				for (Future<Object> f : uploadList) {
					f.get();
				}
			}
			else {
				System.out.println("Choose pref neighbors");
				// Check all the download rate and select preferred neighbors
				for (int i = 0; i < neighborInfo.length; i++) {
					if (neighborInfo[i].getBitField().checkPiecesInterested(bitfield)) {
						prefNeighborList.put(neighborInfo[i].getdownloadRate(),i);
					}
				}
				Set set = prefNeighborList.entrySet();
				Iterator it = set.iterator();
				// Start the threads for those neighbors
				counter = 0;
				System.out.println("Before future while");
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
				System.out.println("After future while");
				log.changeOfPreferredNeighbourLog(prefList);
				// Wait for the future objects here till the upload threads
				// complete their execution
				for (Future<Object> f : uploadList) {
					f.get();
				}
			}
		}
		System.out.println("Uploading done");
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
			{	System.out.println("Client setup starts");
				neighborInfo[i] = new NeighborInfo(totalPieces);
				neighborInfo[i].setPeerID(currPeerID);
				
				if(peerConfigs.getHasWholeFile(i))
					neighborInfo[i].getBitField().setAllBitsTrue();	
				
				setOthersInitialization(neighborInfo[i], host, downloadPort, uploadPort, havePort); // sets up client sockets
			}
			else // If self, set up BitField and server sockets
			{
				peerIndex = i;
				System.out.println("Server setup starts");
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
	
	public synchronized void setSelfInitialization(ServerSocket uploadServerSocket, 
			ServerSocket downloadServerSocket, ServerSocket haveServerSocket, int index) throws Exception
	{
		System.out.println("Server started");
		neighborInfo[index] = new NeighborInfo(peerConfigs.getTotalPieces());
		// Sets up sockets to access the server sockets' I/O streams
		// TODO: Originally 
		Socket downloadSocket = downloadServerSocket.accept();
		Socket uploadSocket = uploadServerSocket.accept();
		Socket haveSocket = haveServerSocket.accept();
		
		System.out.println("Server socket info for peer " + neighborInfo[index].getPeerId() + " from peer " 
		+ peer_id + ":\nUpload Socket: " + uploadSocket.getPort() + "\nDownload Socket: " + downloadSocket.getPort()
		+ "\nHave Socket: " + haveSocket.getPort());
		
		// Put the sockets in the neighborInfo object
		neighborInfo[index].setUploadSocket(uploadSocket);
		neighborInfo[index].setDownloadSocket(downloadSocket);
		neighborInfo[index].setHaveSocket(haveSocket);
		System.out.println("Accepted connections");
		InputStream input = downloadSocket.getInputStream();
		OutputStream output = downloadSocket.getOutputStream();
		
		HandshakeMessage hs = new HandshakeMessage();	
		Message m = new Message();
		int currPeerID;
		//Check if the peer ID is the correct one since we are iterating from the server peer and not client peer
		// Receive handshake message
		hs.receiveMessage(downloadSocket);
		System.out.println("Received a handshake");
		currPeerID = peerConfigs.getPeerList(index+1);
		int hsId = hs.getPeerID();	
		if(!handshakeValid(hs, currPeerID))
			throw new Exception("1Error:  Peer " + peer_id + " received an invalid handshake message from peer " + hs.getPeerID() + "."); 
			
		log.tcpConnectedLog(hs.getPeerID());
		neighborInfo[index].setPeerID(hsId);
		// Send handshake message in response
		hs.setPeerID(peer_id);
		hs.sendMessage(downloadSocket);

		// if a bitfield message is received, update the bitfield of the peer it
		// came from in the neighborInfo array
		m.receiveMessage(input);
		if (m.getType() == Message.bitfield) 
		{
			BitField bf = new BitField(peerConfigs.getTotalPieces());
			bf.setBitFromByte(m.getPayload());
			neighborInfo[index].setBitField(bf);
			
			// Send bitfield in response
			m.setType(Message.bitfield);
			m.setPayload(bitfield.changeBitToByteField());
			m.sendMessage(output);
		}
		m.receiveMessage(input);
		if(m.getType() == Message.notInterested){
			log.notInterestedLog(hsId);
		}
		else if(m.getType() == Message.interested){
			log.interestedLog(hsId);
		}
		else{
			throw new IOException();
		}
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
	
	public synchronized void setOthersInitialization(NeighborInfo otherInfo, String host, int downloadPort, int uploadPort, int havePort)throws Exception
	{
		int neighborID = otherInfo.getPeerId();
		InputStream input;
		OutputStream output;
		System.out.println("Client sockets are being created");
		// set up client sockets for peer apart from local server peer
		Socket uploadClientSocket = new Socket(host, downloadPort);
		Socket downloadClientSocket = new Socket(host, uploadPort);
		Socket haveClientSocket = new Socket(host, havePort);
		
		System.out.println("uploadPort: " + downloadPort + "downloadPort: " + uploadPort + "havePort: " + havePort);
		System.out.println("Client socket info for peer " + otherInfo.getPeerId() + " from peer " 
		+ peer_id + ":\nUpload Socket: " + uploadClientSocket.getPort() + "\nDownload Socket: " + downloadClientSocket.getPort()
		+ "\nHave Socket: " + haveClientSocket.getPort());
		
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
			throw new Exception("2Error:  Peer " + peer_id + " received an invalid handshake message from peer " + hs.getPeerID() + " .");

		// Send bitfield message
		Message m = new Message();
		m.setType(Message.bitfield);
		m.setPayload(bitfield.changeBitToByteField());
		m.sendMessage(output);

		// Receive bitfield
		m.receiveMessage(input);
		if (m.getType() == Message.bitfield) {
			//otherInfo.setBitField(m.getPayload());
			System.out.println("Bitfield is received");
			BitField bf = new BitField(peerConfigs.getTotalPieces());
			bf.setBitFromByte(m.getPayload());
			otherInfo.setBitField(bf);
		}
		// Send an interested or notInterested message depending on the
		// received bitfield contents.
		if (bitfield.checkPiecesInterested(otherInfo.getBitField())) {
			m.setType(Message.interested);
			m.setPayload(null);
			m.sendMessage(output);
		} else {
			m.setType(Message.notInterested);
			m.setPayload(null);
			m.sendMessage(output);
		}
		//Receive interested/not interested messages
		m.receiveMessage(input);
		if(m.getType() == Message.notInterested){
			log.notInterestedLog(otherInfo.getPeerId());
		}
		else{
			log.interestedLog(otherInfo.getPeerId());
		}
	}

	public synchronized boolean handshakeValid(HandshakeMessage hs, int neighborID)
	{
		int peerID = hs.getPeerID();
		
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
