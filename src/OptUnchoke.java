
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * The OptUnchoke class is used in the OptUnchokeTimer.  It 
 * updates the optimistically unchoked peer.
 *
 *@author Abhishek and Anupam
 *
 */
//How can we put this in a callable object since this has to be run parallely
public class OptUnchoke implements Callable<Object> {

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
		try {
		//PeerInfo.optimisticUnchokedPeer = PeerInfo.chokedInterested.get(index);
		while(true) {
			//Select a peer randomly for optUnchoke
			long startTimer = System.currentTimeMillis();
			boolean flag = false;
			while (!flag) {
				index = randomGenerator.nextInt(neighborArray.length);
				if (neighborArray[index].getBitField().checkPiecesInterested(
						bits)
						&& neighborArray[index].setChokeState(0, 1))
					flag = true;
			}
			LoggerPeer log = new LoggerPeer(neighborArray[index].getPeerId());
			log.changeOfOptUnchokedNeighbourLog(PeerInfo.optimisticUnchokedPeer);
			Socket sock = neighborArray[index].getUploadSocket();
			InputStream input = sock.getInputStream();
			OutputStream output = sock.getOutputStream();
			//Send unchoke message
			Message m = new Message();
			m.setType(Message.unchoke);
			m.setPayload(null);
			m.sendMessage(output);
			//Keep track of the timer and break from this inner loop to reselect a peer
			while(true){
				m.receiveMessage(input);
				if(m.getType() == Message.request){
					int index1 = ByteIntConversion.byteArrayToInt(m.getPayload());
					Piece newPiece = file.readFile(index1);
					m.setType(Message.piece);
					m.setPayload(newPiece.getPieceContent());
					m.sendMessage(output);
				}
				if((System.currentTimeMillis() - startTimer) > (optInterval * 1000)){
					m.setType(Message.choke);
					m.setPayload(null);
					m.sendMessage(output);
					neighborArray[index].setChoked();
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
