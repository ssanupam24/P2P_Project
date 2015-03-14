import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * This class receives have messages sent after each download and updates the piece index in the bitfield
 *
 *@author Anupam
 *
 */

public class HaveMessage implements Callable<Object> {

	private int peerId;
	//The selfinfo variable has the neighborinfo record of the client peer that receives a piece through
	//download thread
	private NeighborInfo selfInfo;
	private LoggerPeer logger;
	private NeighborInfo[] neighborArray;
	private Socket sock;
	private InputStream input;
	private OutputStream output;
	private Message m;
	
	public HaveMessage(int id, NeighborInfo selfInfo, LoggerPeer logger, NeighborInfo[] neighborArray) throws IOException{
		this.selfInfo = selfInfo;
		//PeerID is the host(server) peer process
		this.peerId = id;
		this.logger = logger;
		this.neighborArray = neighborArray;
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
			if(m.getType() == Message.have){
				pieceIndex = ByteIntConversion.byteArrayToInt(m.getPayload());
				selfInfo.getBitField().setBitToTrue(pieceIndex);
				logger.haveLog(selfInfo.getPeerId(), pieceIndex);
			}
		}
		return new Object();
	}
	}
