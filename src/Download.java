import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Callable;

/*
 * This class is a callable that is used for downloading a piece. It is used by each client peer
 * @author Anupam
 */
public class Download implements Callable<Object> {
	private int peerId; // ID of the peer the callable is created at
	private NeighborInfo[] neighborArray;
	private NeighborInfo selfInfo; // The NeighborInfo record of the client peer
	private BitField bits;
	private LoggerPeer logger;
	private HandleFile file;
	
	public Download(int peerId, NeighborInfo[] neighborArray, NeighborInfo selfInfo, BitField bits, HandleFile file, LoggerPeer logger) throws IOException{
	 	this.bits = bits;
		this.file = file;
		this.logger = logger;
		this.neighborArray = neighborArray;
		this.peerId = peerId;
		this.selfInfo = selfInfo;
	}
	public Object call() throws Exception{
		Socket s = selfInfo.getDownloadSocket();
		InputStream input = s.getInputStream();
		OutputStream output = s.getOutputStream();
		Message m = new Message();
		int pieceIndex;
		//Remove the check for all the neighbors and add the check for yourself if you are done with downloading
		while(true){
			try{
				if(bits.getFinished()) {
					logger.completeDownloadLog();
					selfInfo.getDownloadSocket().close();
					return new Object();
				}
				m.receiveMessage(input);
				if(m.getType() == Message.unchoke){
					logger.unchokeLog(selfInfo.getPeerId());
					
					while(true){
						if(bits.getFinished()) {
							logger.completeDownloadLog();
							selfInfo.getDownloadSocket().close();
							return new Object();
						}
						pieceIndex = bits.setInterestedPiece(selfInfo.getBitField());
						
						//If there are no interesting piece then the function returns -1
						if(pieceIndex == -1){
							//Send it to upload socket
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
							
							//If choke received then set the piece to false in bitfield to download it later
							if(m.getType() == Message.choke){
								logger.chokeLog(selfInfo.getPeerId());
								bits.setBitToFalse(pieceIndex);
								
								break;
							}
							if(m.getType() == Message.piece){
								byte[] pieceNum = new byte[4];
								byte[] pieceContent = new byte[m.getPayload().length - 4];
								byte[] chunk = m.getPayload();
								pieceNum = Arrays.copyOfRange(chunk, 0, 4);
								pieceContent = Arrays.copyOfRange(chunk, 4, chunk.length);
								Piece p = new Piece(pieceContent, ByteIntConversion.byteArrayToInt(pieceNum));
								
								if(ByteIntConversion.byteArrayToInt(pieceNum) >= bits.getBitPieceIndexLength())
									throw new Exception();
								file.writeFile(p);
								bits.setBitToTrue(ByteIntConversion.byteArrayToInt(pieceNum));
								selfInfo.incdownloadRate();
								logger.downloadingLog(selfInfo.getPeerId(), ByteIntConversion.byteArrayToInt(pieceNum), bits.getCountFinishedPieces());
								
								//Create a have message and send it to all peers
								m.setType(Message.have);
								m.setPayload(pieceNum);
								for(int i = 0; i < neighborArray.length; i++) {
									m.sendMessage(neighborArray[i].getHaveSocket().getOutputStream());
								}
							}
						}
					}
				}
				
			}
			catch(Exception e){
				selfInfo.getDownloadSocket().close();
				return new Object();
			}
		}
	}
}
