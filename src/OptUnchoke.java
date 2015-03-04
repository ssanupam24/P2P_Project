
import java.io.IOException;
import java.util.*;

/**
 * The OptUnchoke class is used in the OptUnchokeTimer.  It 
 * updates the optimistically unchoked peer.
 *
 *@author Abhishek
 *
 */
//How can we put this in a callable object since this has to be run parallely
public class OptUnchoke extends TimerTask {

	private int peerId;
	private NeighborInfo[] neighborArray;
	private BitField bits;
	
	public OptUnchoke(int id, BitField bits, NeighborInfo[] neighborArray){
		this.neighborArray = neighborArray;
		this.bits = bits;
		this.peerId = id;
	}
	@Override
    public void run() 
	{
		Random randomGenerator = new Random();
		int index;
		//PeerInfo.optimisticUnchokedPeer = PeerInfo.chokedInterested.get(index);
		Message m = new Message();
		m.setType(Message.unchoke);
		m.setPayload(null);
		// Now Send Unchoke Message to OptimisticUnchokedPeer. (This part is
		// still left to be added.)
		boolean flag = false;
		while (!flag) {
			index = randomGenerator.nextInt(neighborArray.length);
			if (neighborArray[index].getBitField().checkPiecesInterested(bits)
					&& neighborArray[index].setChokeState(0, 1))
				flag = true;
		}
		
		//sending and receiving messages

		try {
			LoggerPeer log = new LoggerPeer(peerId);

			log.changeOfOptUnchokedNeighbourLog(PeerInfo.optimisticUnchokedPeer);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
	}
}
