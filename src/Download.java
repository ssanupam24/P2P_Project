import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Callable;

/*
 * This class is a callable that is used for downloading a piece
 * @author Anupam
 */
public class Download implements Callable<Object> {
	private int peerId;
	private NeighborInfo[] neighborArray;
	private NeighborInfo selfInfo;
	private BitField bits;
	private LoggerPeer logger;
	private HandleFile file;
	
	public Download(int peerId, NeighborInfo[] neighborArray, NeighborInfo selfInfo, BitField bits, HandleFile file) throws IOException{
		this.bits = bits;
		this.file = file;
		this.logger = new LoggerPeer(peerId);
		this.neighborArray = neighborArray;
		this.peerId = peerId;
		this.selfInfo = selfInfo;
	}
	public Object call() throws IOException{
		Socket s = selfInfo.getDownloadSocket();
		InputStream input = s.getInputStream();
		OutputStream output = s.getOutputStream();
		Message m = new Message();
		boolean finished;
		int pieceIndex;
		while(true){
			finished = true;
			//Check whether all the peers have downloaded the entire file or not
			for(int i = 0; i < neighborArray.length; i++){
				if(!neighborArray[i].hasFinished())
					finished = false;
			}
			//if yes then break from the loop and return null
			if(finished)
				break;
			
			try{
				m.receiveMessage(input);
				if(m.getType() == Message.unchoke){
					logger.unchokeLog(selfInfo.getPeerId());
					while(true){
						pieceIndex = bits.setInterestedPiece(selfInfo.getBitField());
						//If there are no interesting piece then the function returns -1
						if(pieceIndex == -1){
							m.setType(Message.notInterested);
							m.setPayload(null);
							m.sendMessage(output);
							break;
						}
						else{
							//send a request message
							m.setType(Message.request);
							m.setPayload(ByteIntConversion.intToByteArray(pieceIndex));
							m.sendMessage(output);
							m.receiveMessage(input);
							//If choked received then set the piece to false in bitfield to download it later
							if(m.getType() == Message.choke){
								logger.chokeLog(selfInfo.getPeerId());
								bits.setBitToFalse(pieceIndex);
								break;
							}
							if(m.getType() == Message.piece){
								Piece p = new Piece(m.getPayload(), pieceIndex);
								file.writeFile(p);
								bits.setBitToTrue(pieceIndex);
								selfInfo.incAmountOfDownload();
								logger.downloadingLog(selfInfo.getPeerId(), pieceIndex);
								//Create a have message and send it to all peers
								m.setType(Message.have);
								m.setPayload(ByteIntConversion.intToByteArray(pieceIndex));
								for(int i = 0; i < neighborArray.length; i++)
									m.sendMessage(neighborArray[i].getControlSocket().getOutputStream());
							}
						}
					}
				}
				
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		return new Object();
	}
}
