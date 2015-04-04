import java.util.Random;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * This class keeps track of the Pieces that are downloaded from the peers.
 * @author Anupam
 */
public class BitField {
	private final int totPieces;
	private boolean[] bitPieceIndex;
	private AtomicInteger countFinishedPieces;
	private boolean finished;
	private Vector<Integer> randomPieces;
	
	public BitField(int totPieces){
		this.totPieces = totPieces;
		bitPieceIndex = new boolean[totPieces];
		countFinishedPieces = new AtomicInteger(0);
		finished = false;
		randomPieces = new Vector<Integer>();
		for(int i =0; i < totPieces; i++){
			bitPieceIndex[i] = false;
		}
	}
	/*
	 *  This function returns byte array by calculating the number of bytes required to represent 
	 *  the number of pieces and then shift bits according to the value set in the array of boolean flags
	 */
	public synchronized byte[] changeBitToByteField(){
		int noOfBytes;
		int bitIndex;
		int byteIndex;
		if(totPieces % 8 == 0)
			noOfBytes = totPieces / 8;
		else
			noOfBytes = (totPieces / 8) + 1;
		byte[] result = new byte[noOfBytes];
		for(int i =0; i < noOfBytes; i++){
			result[i] = (byte)0;
		}
		for(int i =0; i < totPieces; i++){
			bitIndex = i % 8;
			byteIndex = i / 8;
			if(getBitPieceIndex()[i] == true)
				result[byteIndex] = (byte)((1 << bitIndex) | (result[byteIndex]));
			//Test it with and without
			else
				result[byteIndex] = (byte) (~(1 << bitIndex) & result[byteIndex]);
		}
		return result;
	}
	
	//This function sets the corresponding bit field from the byte array received
	public synchronized void setBitFromByte(byte[] byteArray){
		int bitIndex;
		int byteIndex;
		countFinishedPieces.set(0);
		for(int i = 0; i < totPieces; i++){
			bitIndex = i % 8;
			byteIndex = i / 8;
			//An AND operation can tell you if a bit is set. If it is not set then the result will be 0
			if(((1 << bitIndex) & (byteArray[byteIndex])) == 0) {
				getBitPieceIndex()[i] = false;
			}
			else {
				getBitPieceIndex()[i] = true;
				countFinishedPieces.set(countFinishedPieces.get() + 1);
			}
		}
		if(countFinishedPieces.get() == totPieces)
			finished = true;
	}
	
	//This function is used to change a bit field to binary string of 0's and 1's just for verification.
	public synchronized String changeBitToString(){
		String result = "";
		for(int i = 0; i < totPieces; i++){
			if(getBitPieceIndex()[i] == true)
				result += "1";
			else
				result += "0";
		}
		return result;
	}
	
	public synchronized boolean getFinished(){
		return finished;
	}
	public synchronized int getBitPieceIndexLength(){
		return bitPieceIndex.length;
	}
	public synchronized boolean[] getBitPieceIndex(){
		return bitPieceIndex;
	}
	public synchronized int getCountFinishedPieces(){
		return countFinishedPieces.get();
	}
	
	public synchronized void setBitToTrue(int index){
		if(getBitPieceIndex()[index] == false){
			getBitPieceIndex()[index] = true;
			countFinishedPieces.set(countFinishedPieces.get() + 1);
			if(countFinishedPieces.get() == totPieces)
				finished = true;
		}
	}
	
	public synchronized void setBitToFalse(int index){
		if(getBitPieceIndex()[index] == true){
			getBitPieceIndex()[index] = false;
			countFinishedPieces.set(countFinishedPieces.get() - 1);
			finished = false;
		}
	}
	
	public synchronized void setAllBitsTrue(){
		for(int i =0; i < totPieces; i++){
			getBitPieceIndex()[i] = true;
		}
		countFinishedPieces.set(totPieces);
		finished = true;
	}
 
	//Check if the piece is not present then return interested
	public synchronized boolean checkPiecesInterested(BitField bf){
		for(int i = 0; i < totPieces; i++){
			if((getBitPieceIndex()[i] == false) && (bf.getBitPieceIndex()[i] == true)) 
				return true;
		}
		return false;
	}
	//Check if the interested piece is downloaded then set the flag to true in bitfield array
	public synchronized int setInterestedPiece(BitField bf){
		
		randomPieces.clear();
		Random randomGenerator = new Random();
		for(int i = 0; i < totPieces; i++){
			if((getBitPieceIndex()[i] == false) && (bf.getBitPieceIndex()[i] == true)){
				randomPieces.add(i);
			}
		}
		if(randomPieces.size() == 0)
			return -1;
		else if(randomPieces.size() > 0){
			int counter = randomGenerator.nextInt(randomPieces.size()); 
			getBitPieceIndex()[randomPieces.get(counter)] = true;
			countFinishedPieces.set(countFinishedPieces.get() + 1);
			if(countFinishedPieces.get() == totPieces)
				finished = true;
			return randomPieces.get(counter);
		}
		/*for(int i = 0; i < totPieces; i++){
			if((bitPieceIndex[i] == false) && (bf.bitPieceIndex[i] == true)){
				bitPieceIndex[i] = true;
				countFinishedPieces++;
				if(countFinishedPieces == totPieces)
					finished = true;
				return i;
			}
		}*/
		return -1;
	}
	
}
