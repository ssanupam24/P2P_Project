import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * This class receives have messages sent after each download and updates the piece index in the bitfield
 *
 *@author Anupam and Abhishek
 *
 */

public class HaveMessage implements Callable<Object> {

	private int peerId; // ID of the peer the callable is created at
	private NeighborInfo selfInfo;  // The NeighborInfo record of the client peer
	private LoggerPeer logger;
	private NeighborInfo[] neighborArray;
	private Socket sock;
	private InputStream input;
	private OutputStream output;
	private Message m;
	private BitField bits;
	private boolean fullFile;
	private int noOfPeersToUpload;
	
	public HaveMessage(int id, NeighborInfo selfInfo, LoggerPeer logger, NeighborInfo[] neighborArray, BitField bits, boolean fullFile, int noOfPeersToUpload) throws IOException{
		this.selfInfo = selfInfo;
		this.peerId = id;
		this.logger = logger;
		this.neighborArray = neighborArray;
		this.bits = bits;
		this.fullFile = fullFile;
		this.noOfPeersToUpload = noOfPeersToUpload;
		m = new Message();
		sock = selfInfo.getHaveSocket();
		input = sock.getInputStream();
		output = sock.getOutputStream();
	}
	public Object call() throws Exception 
	{
		boolean finished;
		int pieceIndex;
		while(true){
			
			finished = false;
			int counter = 0;
			//Check whether all the peers have downloaded the entire file or not
			for(int i = 0; i < neighborArray.length; i++){
				if(neighborArray[i].getDoneUpload().get() == 1) {
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
			//if yes then break from the loop and return Object instance
			if(finished)
				return new Object();
			
			try{
			m.receiveMessage(input);
			if(m.getType() == Message.have){
				byte[] payload = m.getPayload();
				pieceIndex = ByteIntConversion.byteArrayToInt(payload);
				selfInfo.getBitField().setBitToTrue(pieceIndex);
				logger.haveLog(selfInfo.getPeerId(), pieceIndex);
				//Send interested/not message here
				if(bits.checkPiecesInterested(selfInfo.getBitField())){
					m.setType(Message.interested);
					m.setPayload(null);
					m.sendMessage(output);
				}
				else{
					m.setType(Message.notInterested);
					m.setPayload(null);
					m.sendMessage(output);
				}
			}
			else if(m.getType() == Message.interested){
				logger.interestedLog(selfInfo.getPeerId());
			}
			else if(m.getType() == Message.notInterested){
				logger.notInterestedLog(selfInfo.getPeerId());
			}
			}
			catch(Exception e){
				return new Object();
			}
		}
	}
	}
