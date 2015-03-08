
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Callable;

/**
 * This class uploads the data to the peers
 * 
 * @author Anupam
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
		//Send Unchoke for the peer that created this callable and then start uploading
		m.setPayload(null);
		m.setType(Message.unchoke);
		m.sendMessage(output);
		long startTimer;
		//Add the logic for neighbor selection or have that in PeerInfo class and submit this callable only
		//for those neighbors
		while(true){
			startTimer = System.currentTimeMillis();
			m.receiveMessage(input);
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
			if(m.getType() == Message.request){
				pieceIndex = ByteIntConversion.byteArrayToInt(m.getPayload());
				Piece p = file.readFile(pieceIndex);
				m.setPayload(p.getPieceContent());
				m.setType(Message.piece);
				m.sendMessage(output);
			}
			if((System.currentTimeMillis() - startTimer) >= (time * 1000)){
				// if the neighbor is not your preferred neighbor, then choke
				m.setType(Message.choke);
				m.setPayload(null);
				m.sendMessage(output);
				break;
			}
		}
		return new Object();
	}
	
}
