
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Callable;

/**
 * This class uploads the data to the peers
 * 
 * @author Anupam and Abhishek
 *
 */
public class Unchoke implements Callable<Object> {
	
	private int peerId;
	private NeighborInfo selfInfo;
	private LoggerPeer logger;
	private NeighborInfo[] neighborArray;
	private Socket sock;
	private InputStream input;
	private OutputStream output;
	private Message m;
	private HandleFile file;
	int time;
	
	public Unchoke(int id, NeighborInfo selfInfo, LoggerPeer logger, NeighborInfo[] neighborArray, int time, HandleFile file) throws IOException{
		this.selfInfo = selfInfo;
		//PeerID is the host(server) peer process
		this.peerId = id;
		this.logger = logger;
		this.neighborArray = neighborArray;
		m = new Message();
		sock = selfInfo.getUploadSocket();
		input = sock.getInputStream();
		output = sock.getOutputStream();
		this.file = file;
		this.time = time;
	}
	public Object call() throws Exception 
	{
		boolean finished;
		int pieceIndex;
		//Send Unchoke to the peer that created this callable and then start uploading
		//Set the choke state and send unchoke only if the neighbor was choked else don't send unchoke
		if(selfInfo.getNeighborChokedState().compareAndSet(0, 1)){
			System.out.println("Unchoked from unchoked callable");
			m.setPayload(null);
			m.setType(Message.unchoke);
			m.sendMessage(output);
		}
		//Change the value of the choke state to preferred if OptUnchoke
		if(selfInfo.getNeighborChokedState().get() == 2)
			selfInfo.getNeighborChokedState().set(1);
		long startTimer = System.currentTimeMillis();
		//Add the logic for neighbor selection or have that in PeerInfo class and submit this callable only
		//for those neighbors, seems like it's efficient
		while(true){
			//Add the not interested message condition		
			m.receiveMessage(input);
			finished = true;
			//Check whether all the peers have downloaded the entire file or not
			for(int i = 0; i < neighborArray.length; i++){
				if(!neighborArray[i].hasFinished()) {
					finished = false;
					break;
				}
			}
			//if yes then break from the loop and return an Object to keep track of the task in peerProcess
			if(finished)
				break;
			if(m.getType() == Message.notInterested){
				logger.notInterestedLog(selfInfo.getPeerId());
				//break or do something
				break;
			}
			if(m.getType() == Message.request){
				logger.requestLog(selfInfo.getPeerId(), true);
				pieceIndex = ByteIntConversion.byteArrayToInt(m.getPayload());
				Piece p = file.readFile(pieceIndex);
				m.setPayload(p.getPieceContent());
				m.setType(Message.piece);
				m.sendMessage(output);
			}
			if((System.currentTimeMillis() - startTimer) >= (time * 1000)){
				// if the neighbor is not your preferred neighbor, then choke
					//see or delete it
				//m.receiveMessage(input);
				if(selfInfo.getNeighborChokedState().compareAndSet(1, 0)) {
					m.setType(Message.choke);
					m.setPayload(null);
					m.sendMessage(output);
				}
				System.out.println("Timer expired" + selfInfo.getPeerId());
				break;
			}
		}
		return new Object();
	}
	
}
