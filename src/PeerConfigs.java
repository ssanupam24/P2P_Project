import java.io.FileNotFoundException;
import java.util.ArrayList;


public class PeerConfigs {
	private final int prefNeighbors;
	private final int timeUnchoke;
	private final int timeOptUnchoke;
	private final String fileName;
	private final int sizeOfFile;
	private final int sizeOfPiece;
	private final int sizeOfLastPiece;
	private final int totalPieces;
	private final int totalPeers;
	private final ArrayList<Integer> peerList;
	private final ArrayList<String> hostList;
	private final ArrayList<Integer> downloadPortList;
	private final ArrayList<Integer> uploadPortList;
	private final ArrayList<Boolean> hasWholeFile;
	private final ArrayList<Integer> havePortList;
	
	public PeerConfigs(String commonConfig, String peerConfig) throws FileNotFoundException{
		//Write the code to read configs here
	}
	
	public int getPrefNeighbors(){
		 return prefNeighbors;
	}
	public int getTimeUnchoke(){
		return timeUnchoke;
	}
	public int getTimeOptUnchoke(){
		return timeOptUnchoke;
	}
	public String getFileName(){
		return fileName;
	}
	public int getSizeOfFile(){
		return sizeOfFile;
	}
	public int getSizeOfPiece(){
		return sizeOfPiece;
	}
	public int getSizeOfLastPiece(){
		return sizeOfLastPiece;
	}
	public int getTotalPieces(){
		return totalPieces;
	}
	public int getTotalPeers(){
		return totalPeers;
	}
	public ArrayList<Integer> getPeerList(){
		return peerList;
	}
	public ArrayList<String> getHostList(){
		return hostList;
	}
	public ArrayList<Integer> getDownPortList(){
		return downloadPortList;
	}
	public ArrayList<Integer> getUpPortList(){
		return uploadPortList;
	}
	public ArrayList<Integer> getHavePortList(){
		return havePortList;
	}
	public ArrayList<Boolean> getHasWholeFile(){
		return hasWholeFile;
	}
	
}
