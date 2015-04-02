
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
//How can we put this in a callable object since this has to be run parallely
public class OptUnchoke implements Callable<Object> {
	//PeerID is the host(server) peer process
	private int peerId;
	private NeighborInfo[] neighborArray;
	private BitField bits;
	private LoggerPeer logger;
	private int optInterval;
	private HandleFile file;
	
	public OptUnchoke(int id, BitField bits, NeighborInfo[] neighborArray, LoggerPeer logger, int optInterval, HandleFile file){
		this.neighborArray = neighborArray;
		this.bits = bits;
		this.peerId = id;
		this.logger = logger;
		this.optInterval = optInterval;
	}
	public Object call() throws Exception 
	{
		Random randomGenerator = new Random();
		int index = 0;
		int index1 = 0;
		boolean flag;
		boolean finished;
		long startTimer;
		Socket sock;
		InputStream input;
		OutputStream output;
		int counter = 0;
		try {
		//PeerInfo.optimisticUnchokedPeer = PeerInfo.chokedInterested.get(index);
		while(true) {
			//Select a peer randomly for optUnchoke
			startTimer = System.currentTimeMillis();
			flag = false;
			finished = true;
			//Check whether all the peers have downloaded the entire file or not
			for(int i = 0; i < neighborArray.length; i++){
				if(!neighborArray[i].hasFinished()) {
					finished = false;
					break;
				}
			}
			//if yes then break from the loop and return null
			if(finished)
				return new Object();
			while (!flag) {
				index = randomGenerator.nextInt(neighborArray.length);
				if((neighborArray[index].getNeighborChokedState().get() == 0) && (neighborArray[index].getBitField().checkPiecesInterested(bits))){
					flag = true;
					finished = false;
				}
				counter = 0;
				for(int i = 0; i < neighborArray.length; i++){
					if(neighborArray[i].hasFinished()) {
						counter++;
					}
				}
				if(counter == neighborArray.length){
					finished = true;
					flag = true;
				}
			}
			if(finished)
				return new Object();
			logger.changeOfOptUnchokedNeighbourLog(neighborArray[index].getPeerId());
			sock = neighborArray[index].getUploadSocket();
			input = sock.getInputStream();
			output = sock.getOutputStream();
			Message m = new Message();
			//Send unchoke message only if choked
			//Set the choke state and send unchoke only if the neighbor was choked else don't send unchoke
			if(neighborArray[index].getNeighborChokedState().compareAndSet(0, 2)){
					System.out.println("Unchoked from OptUnchoked");
					m.setType(Message.unchoke);
					m.setPayload(null);
					m.sendMessage(output);
			}
			
			//Keep track of the timer and break from this inner loop to reselect a peer
			while(true){
				//Check to see if all the neighbors are done downloading
				finished = true;
				//Check whether all the peers have downloaded the entire file or not
				for(int i = 0; i < neighborArray.length; i++){
					if(!neighborArray[i].hasFinished()) {
						finished = false;
						break;
					}
				}
				//if yes then break from the loop and return null
				if(finished)
					return new Object();
				m.receiveMessage(input);
				System.out.println("Did not get stuck waiting to receive a message in OptUnchoke.");
				//Add the not interested thing here
				if(m.getType() == Message.notInterested){
					logger.notInterestedLog(neighborArray[index].getPeerId());
					System.out.println("Received notInterested in OptUnchoke from peer.");
					//break or do something
					break;
				}
				if(m.getType() == Message.request){
					logger.requestLog(neighborArray[index].getPeerId(), true);
					index1 = ByteIntConversion.byteArrayToInt(m.getPayload());
					Piece newPiece = file.readFile(index1);
					m.setType(Message.piece);
					m.setPayload(newPiece.getPieceContent());
					m.sendMessage(output);
				}
				if((System.currentTimeMillis() - startTimer) >= (optInterval * 1000)){
					// if the neighbor is your OptUnchoked neighbor, then choke and set the choke state
					//see the below or delete
						//m.receiveMessage(input);
					if(neighborArray[index].getNeighborChokedState().compareAndSet(2, 0)) {
						m.setType(Message.choke);
						m.setPayload(null);
						m.sendMessage(output);
					}
					break;
				}
			}
		}
		}
		catch (IOException ex) {
				ex.printStackTrace();
		}
		return new Object();
	}
}
