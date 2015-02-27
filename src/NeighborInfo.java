import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * @author Anupam and Gloria
 */
public class NeighborInfo {
	public int peer_id;
	public AtomicInteger stateOfChoke;
	public BitField bitField;
	public Socket uploadSocket;
	public Socket downloadSocket;
	public Socket controlSocket;
	public int amountOfDownload;
	
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
	public void setBitField(BitField newBitField){
		bitField = newBitField;
	}
	public void setBitField(byte[] BitFieldNew){
		bitField.setBitFromByte(BitFieldNew);
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
}
