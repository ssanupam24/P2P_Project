/*
 * @author Anupam and Gloria
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
			filePointer.getFile().close();
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
		//TODO:Change the below tree map to something else to store duplicate values
		Map<Integer, Integer> prefNeighborList = new Hashtable<Integer, Integer>(); // for peers with the whole file
		TreeMap<Integer, Vector<Integer>> prefNeighborList1 = new TreeMap<Integer, Vector<Integer>>(Collections.reverseOrder()); // for peers without the whole file
		ExecutorService uploadPool = Executors.newFixedThreadPool(peerConfigs.getPrefNeighbors());
		boolean finished;
		Random randomGenerator = new Random();
		int counter;
		int index;
		Vector<Future<Object>> uploadList = new Vector<Future<Object>>();
		//Message m1 = new Message();
		while(true){
			finished = true;
			//Check whether all the peers have downloaded the entire file or not
			for(int i = 0; i < neighborInfo.length; i++){
				neighborInfo[i].getNeighborChokedState().compareAndSet(1, 0);
				if (!neighborInfo[i].hasFinished())
					finished = false;
			}
			//if yes then break from the loop and return null
			if(finished)
				break;
			prefNeighborList.clear();
			prefNeighborList1.clear();
			prefList.clear();
			uploadList.clear();
			if(fullFile) {
				System.out.println("Choose pref neighbors");
				counter = 0;
				for(int k = 0; k < neighborInfo.length && counter < peerConfigs.getPrefNeighbors(); k++){
					if((neighborInfo[k].getBitField().checkPiecesInterested(bitfield)) && 
							(neighborInfo[k].getNeighborChokedState().get() == 0)){
						counter++;
					}
				}
				while(counter != 0) {
					index = randomGenerator.nextInt(neighborInfo.length);
					while(prefNeighborList.containsKey(neighborInfo[index].getPeerId()) || (!neighborInfo[index].getBitField().checkPiecesInterested(bitfield))
							|| (neighborInfo[index].getNeighborChokedState().get() != 0)) {
						index = randomGenerator.nextInt(neighborInfo.length);
					}
					prefNeighborList.put(neighborInfo[index].getPeerId(),index);
					counter--;
				}
				System.out.println("Before future for loop");
				Set<Entry<Integer, Integer>> set = prefNeighborList.entrySet();
				Iterator<Entry<Integer, Integer>> it = set.iterator();
				while (it.hasNext()) {
					Map.Entry<Integer, Integer> m = (Map.Entry<Integer, Integer>) it.next();
					prefList.add((Integer)m.getKey());
					Future<Object> uploadFuture = uploadPool.submit(new Unchoke(peer_id, neighborInfo[(Integer) m.getValue()],log,
							neighborInfo, unchokeInterval, filePointer));
					uploadList.add(uploadFuture);
				}
				System.out.println("After future for loop");
				if(prefList.size() != 0)
					log.changeOfPreferredNeighbourLog(prefList);
				// Wait for the future objects here till the upload threads
				// complete their execution
				if(uploadList.size() != 0) {
					for (Future<Object> f : uploadList) {
						f.get();
					}
				}
			}
			else {
				System.out.println("Choose pref neighbors");
				// Check all the download rate and select preferred neighbors
				for (int i = 0; i < neighborInfo.length; i++) {
					if ((neighborInfo[i].getBitField().checkPiecesInterested(bitfield))&& 
							(neighborInfo[i].getNeighborChokedState().get() == 0)) {
						if(prefNeighborList1.containsKey(neighborInfo[i].getdownloadRate())){
							prefNeighborList1.get(neighborInfo[i].getdownloadRate()).add(i);
						}
						else{
							Vector<Integer> v = new Vector<Integer>();
							v.add(i);
							prefNeighborList1.put(neighborInfo[i].getdownloadRate(),v);
						}
						System.out.println("This client that did not start with the full file found that peer " + neighborInfo[i].getPeerId() + " is interested in it.");
					}
				}
				Set<Entry<Integer, Vector<Integer>>> set = prefNeighborList1.entrySet();
				Iterator<Entry<Integer, Vector<Integer>>> it = set.iterator();
				// Start the threads for those neighbors
				counter = 0;
				System.out.println("Before future while");
				for(int i = 0; i < neighborInfo.length; i++){
					neighborInfo[i].resetDownload();
				}
				while (it.hasNext()) {
					Map.Entry<Integer, Vector<Integer>> m = (Map.Entry<Integer, Vector<Integer>>) it.next();
					Vector<Integer> v = m.getValue();
					while(v.size() > 0){
						prefList.add(neighborInfo[v.get(0)].getPeerId());
						Future<Object> uploadFuture = uploadPool.submit(new Unchoke(peer_id,
								neighborInfo[v.get(0)], log,
								neighborInfo, unchokeInterval, filePointer));
						uploadList.add(uploadFuture);
						v.remove(0);
						counter++;
						if (counter >= peerConfigs.getPrefNeighbors())
							break;
					}
					if (counter >= peerConfigs.getPrefNeighbors())
						break;
				}
				System.out.println("After future while");
				if(prefList.size() != 0)
					log.changeOfPreferredNeighbourLog(prefList);
				// Wait for the future objects here till the upload threads
				// complete their execution
				if(uploadList.size() != 0){
					for (Future<Object> f : uploadList) {
						f.get();
					}
				}
			}
			//Finally the pool is shutdown 
			uploadPool.shutdownNow();
			uploadPool = Executors.newFixedThreadPool(peerConfigs.getPrefNeighbors());
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
				//Redundant logic as we are setting up the bitfield from the one received from peer
				/*if(peerConfigs.getHasWholeFile(i)) {
					BitField bf = new BitField(peerConfigs.getTotalPieces());
					bf.setAllBitsTrue();
					neighborInfo[i].setBitField(bf);
				}*/
				
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
		
		// Put the sockets in the neighborInfo object
		neighborInfo[index].setUploadSocket(uploadSocket);
		neighborInfo[index].setDownloadSocket(downloadSocket);
		neighborInfo[index].setHaveSocket(haveSocket);
		System.out.println("Accepted connections");
		InputStream input = downloadSocket.getInputStream();
		OutputStream output = downloadSocket.getOutputStream();
		
		HandshakeMessage hs = new HandshakeMessage();
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
		Message m = new Message();
		m.receiveMessage(input);
		if (m.getType() == Message.bitfield) 
		{
			BitField bf = new BitField(peerConfigs.getTotalPieces());
			bf.setBitFromByte(m.getPayload());
			neighborInfo[index].setBitField(bf);
			
			//System.out.println(neighborInfo[index].getBitField().changeBitToString());
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
		Message m3 = new Message();
		m3.receiveMessage(input);
		if (m3.getType() == Message.bitfield) {
			//otherInfo.setBitField(m.getPayload());
			System.out.println("Bitfield is received");
			BitField bf = new BitField(peerConfigs.getTotalPieces());
			bf.setBitFromByte(m3.getPayload());
			otherInfo.setBitField(bf);
			
			//System.out.println(otherInfo.getBitField().changeBitToString());
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
