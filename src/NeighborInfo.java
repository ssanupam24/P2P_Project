import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * @author Anupam and Gloria
 * This class saves all the required information of all the peers
 */
public class NeighborInfo {
	private int peer_id;
	private AtomicInteger chokedByNeighborState; // indicates whether this neighbor is choking the peer
	private AtomicInteger neighborChokedState; // indicates whether this neighbor is choked by the peer
	private BitField bitField;
	private Socket uploadSocket;
	private Socket downloadSocket;
	private Socket haveSocket;
	private AtomicInteger downloadRate;
	
	public NeighborInfo(int numPieces) throws UnknownHostException, IOException
	{
		chokedByNeighborState = new AtomicInteger(0);
		neighborChokedState = new AtomicInteger(0);
		bitField = new BitField(numPieces);
		downloadRate = new AtomicInteger(0);
	}
	public synchronized int getPeerId(){
		return peer_id;
	}
	public synchronized BitField getBitField(){
		return bitField;
	}
	public synchronized int getChokedByNeighborState(){
		return chokedByNeighborState.get();
	}
	public synchronized int getNeighborChokedStateValue(){
		return neighborChokedState.get();
	}
	public synchronized AtomicInteger getNeighborChokedState(){
		return neighborChokedState;
	}
	public synchronized Socket getUploadSocket(){
		return uploadSocket;
	}
	public synchronized Socket getDownloadSocket(){
		return downloadSocket;
	}
	public synchronized Socket getHaveSocket(){
		return haveSocket;
	}
	public synchronized int getdownloadRate(){		
		return downloadRate.get();		
	}		
	public synchronized void resetDownload(){		
		downloadRate.set(0);		
	}
	public synchronized void setPeerID(int peer_id){		
		this.peer_id = peer_id;		
	}
	public synchronized void setDownloadAmount(int amount){		
		downloadRate.set(amount);		
	}
	public synchronized void incdownloadRate(){		
		downloadRate.set(downloadRate.get() + 1);		
	}
	public synchronized void setNeighborChoked(){
		neighborChokedState.set(0);
	}
	public synchronized void setNeighborOptUnchoked(){
		neighborChokedState.set(2);
	}
	public synchronized void setNeighborPreferred(){
		neighborChokedState.set(1);
	}	
	public synchronized void setChokedByNeighbor(){
		chokedByNeighborState.set(0);
	}
	public synchronized void setOptUnchokedByNeighbor(){
		chokedByNeighborState.set(2);
	}
	public synchronized void setPreferredByNeighbor(){
		chokedByNeighborState.set(1);
	}	
	public synchronized void setBitField(BitField newBitField){
		bitField = newBitField;
	}
	public synchronized void setBitField(byte[] BitFieldNew){
		bitField.setBitFromByte(BitFieldNew);
	}
	public synchronized void setUploadSocket(Socket socket){
		uploadSocket = socket;
	}
	public synchronized void setDownloadSocket(Socket socket){
		downloadSocket = socket;
	}
	public synchronized void setHaveSocket(Socket socket){
		haveSocket = socket;
	}
	public synchronized Boolean hasFinished(){
		return bitField.getFinished();
	}
	public synchronized void setBitInBitField(int index){
		bitField.setBitToTrue(index);
	}
	public synchronized Boolean setChokedByNeighborState(int value, int newValue){
		return chokedByNeighborState.compareAndSet(value, newValue);
	}
	public synchronized Boolean setNeighborChokedState(int value, int newValue){
		return neighborChokedState.compareAndSet(value, newValue);
	}	
}
