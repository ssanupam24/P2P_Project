import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * @author Anupam and Gloria
 */
public class NeighborInfo {
	private int peer_id;
	private AtomicInteger stateOfChoke;
	private BitField bitField;
	private Socket uploadSocket;
	private Socket downloadSocket;
	private Socket controlSocket;
	private int amountOfDownload;
	
	public NeighborInfo(int peerID, int numPieces) throws UnknownHostException, IOException
	{
		peer_id = peerID;
		stateOfChoke = new AtomicInteger(0);
		bitField = new BitField(numPieces);
		resetDownload();
	}
	public int getPeerId(){
		return peer_id;
	}
	public BitField getBitField(){
		return bitField;
	}
	public int getStateOfChoke(){
		return stateOfChoke.get();
	}
	public Socket getUploadSocket(){
		return uploadSocket;
	}
	public Socket getDownloadSocket(){
		return downloadSocket;
	}
	public Socket getControlSocket(){
		return controlSocket;
	}
	public int getAmountOfDonwload(){
		return amountOfDownload;
	}
	public void resetDownload(){
		amountOfDownload = 0;
	}
	public void setChoked(){
		stateOfChoke.set(0);
	}
	public void setUnchoked(){
		stateOfChoke.set(1);
	}
	public void setDownloadAmount(int amount){
		amountOfDownload = amount;
	}
	public void setBitField(BitField newBitField){
		bitField = newBitField;
	}
	public void setBitField(byte[] BitFieldNew){
		bitField.setBitFromByte(BitFieldNew);
	}
	public void setUploadSocket(Socket socket){
		uploadSocket = socket;
	}
	public void setDownloadSocket(Socket socket){
		downloadSocket = socket;
	}
	public void setControlSocket(Socket socket){
		controlSocket = socket;
	}
	public Boolean hasFinished(){
		return bitField.getFinished();
	}
	public void setBitInBitField(int index){
		bitField.setBitToTrue(index);
	}
	public int incAmountOfDownload(){
		return amountOfDownload++;
	}
	public Boolean setChokeState(int value, int newValue){
		return stateOfChoke.compareAndSet(value, newValue);
	}
}
