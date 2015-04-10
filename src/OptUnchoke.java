
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
	Socket sock;
	InputStream input;
	OutputStream output;
	
	public OptUnchoke(int id, BitField bits, NeighborInfo[] neighborArray, LoggerPeer logger, int optInterval, HandleFile file){
		this.neighborArray = neighborArray;
		this.bits = bits;
		this.peerId = id;
		this.logger = logger;
		this.file = file;
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
		
		int counter = 0;
		try {
		//PeerInfo.optimisticUnchokedPeer = PeerInfo.chokedInterested.get(index);
		while(true) {
			//Select a peer randomly for optUnchoke
			flag = false;
			finished = false;
			while (!flag) {
				index = randomGenerator.nextInt(neighborArray.length);
				if((neighborArray[index].getBitField().checkPiecesInterested(bits)) && (neighborArray[index].getNeighborChokedState().get() == 0)){
					if((neighborArray[index].getNeighborChokedState().get() == 0)) {
						flag = true;
						neighborArray[index].getNeighborChokedState().set(2);
					}
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
			if(neighborArray[index].getNeighborChokedState().get() == 2){
				System.out.println("Unchoked from OptUnchoked");
				m.setType(Message.unchoke);
				m.setPayload(null);
				m.sendMessage(output);
			}
			startTimer = System.currentTimeMillis();
			//Keep track of the timer and break from this inner loop to reselect a peer
			while(true){
				
				//If the selected peer is done downloading then exit from the loop and select a new peer
				if(neighborArray[index].getBitField().getFinished())
					break;
				System.out.println("Before receiving msg in OptUnchoke.\nPeer " + neighborArray[index].getPeerId() + " has total pieces: "
						+ neighborArray[index].getBitField().getCountFinishedPieces());
				m.receiveMessage(input);		
				System.out.println("Did not get stuck waiting to receive a message in OptUnchoke.");
				//Add the not interested thing here
				if(m.getType() == Message.notInterested){
					logger.notInterestedLog(neighborArray[index].getPeerId());
					System.out.println("Received notInterested in OptUnchoke from peer.");
					neighborArray[index].getNeighborChokedState().compareAndSet(2, 0);
					//break or do something
					break;
				}
				if(m.getType() == Message.request){
					index1 = ByteIntConversion.byteArrayToInt(m.getPayload());
					System.out.println("Received a request in OptUnchoked");
					//logger.requestLog(neighborArray[index].getPeerId(), true, index1);
					Piece newPiece = file.readFileForOpt(index1);
					System.out.println("Piece is read from Optunchoke and now i am sending the piece");
					byte[] piecelen = ByteIntConversion.intToByteArray(newPiece.getPieceNum());
					byte[] chunk = new byte[piecelen.length + newPiece.getPieceContent().length];
					System.arraycopy(piecelen, 0, chunk, 0, piecelen.length);
					System.arraycopy(newPiece.getPieceContent(), 0, chunk, piecelen.length, newPiece.getPieceContent().length);				
					m.setType(Message.piece);
					m.setPayload(chunk);
					m.sendMessage(output);
					System.out.println("Successfully sent a piece from Optunchoke");
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
					System.out.println("Timer expired in OptUnchoke callable :(");
					break;
				}
			}
			//Just to play safe.
			neighborArray[index].getNeighborChokedState().compareAndSet(2, 0);
			}
		}
		catch (Exception ex) {
			throw new Exception();
		}
		
	}
}
