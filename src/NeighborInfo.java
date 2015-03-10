import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * @author Anupam and Gloria
 */
public class NeighborInfo {
	private int peer_id;
	private AtomicInteger chokedByNeighborState; // indicates whether this neighbor is choking the peer
	private AtomicInteger neighborChokedState; // indicates whether this neighbor is choked by the peer
	private BitField bitField;
	private Socket uploadSocket;
	private Socket downloadSocket;
	private Socket controlSocket;
	
	public NeighborInfo(int peerID, int numPieces) throws UnknownHostException, IOException
	{
		peer_id = peerID;
		chokedByNeighborState = new AtomicInteger(0);
		neighborChokedState = new AtomicInteger(0);
		bitField = new BitField(numPieces);
	}
	public int getPeerId(){
		return peer_id;
	}
	public BitField getBitField(){
		return bitField;
	}
	public int getChokedByNeighborState(){
		return chokedByNeighborState.get();
	}
	public int getNeighborChokedState(){
		return neighborChokedState.get();
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
	public void setNeighborChoked(){
		neighborChokedState.set(0);
	}
	public void setNeighborOptUnchoked(){
		neighborChokedState.set(1);
	}
	public void setNeighborPreferred(){
		neighborChokedState.set(2);
	}	
	public void setChokedByNeighbor(){
		chokedByNeighborState.set(0);
	}
	public void setOptUnchokedByNeighbor(){
		chokedByNeighborState.set(1);
	}
	public void setPreferredByNeighbor(){
		chokedByNeighborState.set(2);
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
	public Boolean setChokedByNeighborState(int value, int newValue){
		return chokedByNeighborState.compareAndSet(value, newValue);
	}
	public Boolean setNeighborChokedState(int value, int newValue){
		return neighborChokedState.compareAndSet(value, newValue);
	}	
}
