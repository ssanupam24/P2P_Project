
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
		Message m = new Message();
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
				break;
			while (!flag) {
				index = randomGenerator.nextInt(neighborArray.length);
				if ((neighborArray[index].getPeerId() != peerId)
						&& (neighborArray[index].getBitField().checkPiecesInterested(bits))
						&& (neighborArray[index].setChokedByNeighborState(0, 1)))
					flag = true;
			}
			logger.changeOfOptUnchokedNeighbourLog(neighborArray[index].getPeerId());
			sock = neighborArray[index].getUploadSocket();
			input = sock.getInputStream();
			output = sock.getOutputStream();
			//Send unchoke message
			m.setType(Message.unchoke);
			m.setPayload(null);
			m.sendMessage(output);
			//Keep track of the timer and break from this inner loop to reselect a peer
			while(true){
				m.receiveMessage(input);
				if(m.getType() == Message.request){
					index1 = ByteIntConversion.byteArrayToInt(m.getPayload());
					Piece newPiece = file.readFile(index1);
					m.setType(Message.piece);
					m.setPayload(newPiece.getPieceContent());
					m.sendMessage(output);
				}
				if((System.currentTimeMillis() - startTimer) >= (optInterval * 1000)){
					// if the neighbor is not your preferred neighbor, then choke
					if(neighborArray[index].getNeighborChokedState() != 2)
					{
						m.setType(Message.choke);
						m.setPayload(null);
						m.sendMessage(output);
						neighborArray[index].setChokedByNeighbor();
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
