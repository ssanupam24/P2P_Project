
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
public class Unchoke implements Callable<Integer> {
	
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
	public Integer call() throws Exception 
	{
		int pieceIndex;
		if(selfInfo.getNeighborChokedState().get() == 2){
			return selfInfo.getPeerId();
		}
		try{
		//Send Unchoke to the peer that created this callable and then start uploading
		//Set the choke state and send unchoke only if the neighbor was choked else don't send unchoke
		if(selfInfo.getNeighborChokedState().compareAndSet(0, 1)){
			// System.out.println("Unchoked from unchoked callable");
			m.setPayload(null);
			m.setType(Message.unchoke);
			m.sendMessage(output);
		}
		long startTimer = System.currentTimeMillis();
		
		while(true){
			
			//if(selfInfo.getBitField().getFinished())
				//return selfInfo.getPeerId();
			//Add the not interested message condition	
			
			m.receiveMessage(input);
			if(m.getType() == Message.notInterested){
				logger.notInterestedLog(selfInfo.getPeerId());
				selfInfo.getNeighborChokedState().compareAndSet(1, 0);
				while(true){
					if((System.currentTimeMillis() - startTimer) >= (time * 1000))
						break;
				}
				//break or do something
				break;
			}
			if(m.getType() == Message.request){
				pieceIndex = ByteIntConversion.byteArrayToInt(m.getPayload());
				//logger.requestLog(selfInfo.getPeerId(), true, pieceIndex);
				Piece p = file.readFile(pieceIndex);
				byte[] piecelen = ByteIntConversion.intToByteArray(p.getPieceNum());
				byte[] chunk = new byte[piecelen.length + p.getPieceContent().length];
				System.arraycopy(piecelen, 0, chunk, 0, piecelen.length);
				System.arraycopy(p.getPieceContent(), 0, chunk, piecelen.length, p.getPieceContent().length);
				m.setPayload(chunk);
				m.setType(Message.piece);
				m.sendMessage(output);
			}
			if((System.currentTimeMillis() - startTimer) >= (time * 1000)){
				// if the neighbor is not your preferred neighbor, then choke
				if(selfInfo.getNeighborChokedState().compareAndSet(1, 0)) {
					m.setType(Message.choke);
					m.setPayload(null);
					m.sendMessage(output);
				}
				//System.out.println("Timer expired" + selfInfo.getPeerId());
				break;
			}
		}
		}
		catch(Exception e){
			selfInfo.getDoneUpload().set(1);
			throw new Exception();
			//return selfInfo.getPeerId();
		}
		//To play safe
		//selfInfo.getNeighborChokedState().compareAndSet(1, 0);
		return selfInfo.getPeerId();
	}
	
}
