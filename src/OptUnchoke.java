
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * The OptUnchoke class is used to update the optimistically unchoked peer.
 * It also sends pieces and receives request to/from peer
 * 
 *@author Abhishek and Anupam
 *
 */

public class OptUnchoke implements Callable<Object> {
	private int peerId;  // ID of the peer the callable is created at
	private NeighborInfo[] neighborArray;
	private BitField bits;
	private LoggerPeer logger;
	private int optInterval;
	private HandleFile file;
	private int noOfPeersToUpload;
	private boolean fullFile;
	Socket sock;
	InputStream input;
	OutputStream output;
	
	public OptUnchoke(int id, BitField bits, NeighborInfo[] neighborArray, LoggerPeer logger, int optInterval, HandleFile file, boolean fullFile, int noOfPeersToUpload){
		this.neighborArray = neighborArray;
		this.bits = bits;
		this.peerId = id;
		this.logger = logger;
		this.file = file;
		this.optInterval = optInterval;
		this.fullFile = fullFile;
		this.noOfPeersToUpload = noOfPeersToUpload;
	}
	public Object call() throws Exception 
	{
		Random randomGenerator = new Random();
		int index = 0;
		int index1 = 0;
		boolean flag;
		boolean finished;
		long startTimer;
		
		int counter = 0;
		
		while(true) {
			try {
			flag = false;
			finished = false;
			counter = 0;
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
			if(finished)
				return new Object();
			
			//Select a peer randomly for optUnchoke			
			while (!flag) {
				index = randomGenerator.nextInt(neighborArray.length);
				if((neighborArray[index].getBitField().checkPiecesInterested(bits)) && (neighborArray[index].getNeighborChokedState().get() == 0) && (neighborArray[index].getDoneUpload().get() == 0)){
						flag = true;
						neighborArray[index].getNeighborChokedState().set(2);
				}
				
			}
			
			logger.changeOfOptUnchokedNeighbourLog(neighborArray[index].getPeerId());
			sock = neighborArray[index].getUploadSocket();
			input = sock.getInputStream();
			output = sock.getOutputStream();
			Message m = new Message();
			
			//Send unchoke message only if choked
			//Set the choke state and send unchoke only if the neighbor was choked else don't send unchoke
			if((neighborArray[index].getNeighborChokedState().get() == 2) && (neighborArray[index].getDoneUpload().get() == 0)){
				m.setType(Message.unchoke);
				m.setPayload(null);
				m.sendMessage(output);
			}
			startTimer = System.currentTimeMillis();
			//Keep track of the timer and break from this inner loop to reselect a peer
			while(true){
				
				//If the selected peer is done downloading then exit from the loop and select a new peer
				if(neighborArray[index].getDoneUpload().get() == 1)
					break;
				m.receiveMessage(input);		
				
				if(m.getType() == Message.notInterested){
					logger.notInterestedLog(neighborArray[index].getPeerId());
					neighborArray[index].getNeighborChokedState().compareAndSet(2, 0);
					while(true){
						if((System.currentTimeMillis() - startTimer) >= (optInterval * 1000))
							break;
					}
					break;
				}
				if(m.getType() == Message.request){
					index1 = ByteIntConversion.byteArrayToInt(m.getPayload());
					Piece newPiece = file.readFileForOpt(index1);
					byte[] piecelen = ByteIntConversion.intToByteArray(newPiece.getPieceNum());
					byte[] chunk = new byte[piecelen.length + newPiece.getPieceContent().length];
					System.arraycopy(piecelen, 0, chunk, 0, piecelen.length);
					System.arraycopy(newPiece.getPieceContent(), 0, chunk, piecelen.length, newPiece.getPieceContent().length);				
					m.setType(Message.piece);
					m.setPayload(chunk);
					m.sendMessage(output);
				}
				if((System.currentTimeMillis() - startTimer) >= (optInterval * 1000)){
					// if the neighbor is your OptUnchoked neighbor, then choke and set the choke state
					if(neighborArray[index].getNeighborChokedState().compareAndSet(2, 0)) {
						m.setType(Message.choke);
						m.setPayload(null);
						m.sendMessage(output);
					}
					break;
				}
			}
			}
			catch (Exception ex) {
				neighborArray[index].getDoneUpload().compareAndSet(0, 1);
			}
			neighborArray[index].getNeighborChokedState().compareAndSet(2, 0);		
		}
	}
}
