
public class BitField {
	private final int totPieces;
	private boolean[] bitPieceIndex;
	private int countFinishedPieces;
	private boolean finished;
	
	public Bitfield(int totPieces){
		//Initialize here
		this.totPieces = totPieces;
		bitPieceIndex = new boolean[totPieces];
		countFinishedPieces = 0;
		finished = false;
		for(int i =0; i < totPieces; i++){
			bitPieceIndex[i] = false;
		}
	}
	public synchronized byte[] changeBitToByteField(){
		//return byte array parsing the bits in the 
	}
	public synchronized boolean getFinished(){
		return finished;
	}
	public synchronized void setBitToTrue(int index){
		
	}
	public synchronized void setBitToFalse(int index){
		
	}
	public synchronized void setAllBitsTrue(){
		
	}
	//This function is used to change a bit field to binary string of 0's and 1's
	public synchronized String changeBitToString(){
		
	} 
	//Check if the piece is not present then return interested
	public synchronized boolean checkPiecesInterested(BitField bf){
		for(int i = 0; i < totPieces; i++){
			if((bitPieceIndex[i] == false) && (bf.bitPieceIndex[i] == true)) 
				return true;
		}
		return false;
	}
	//Check if the interested piece is downloaded then set the flag to true in bitfield array
	public synchronized int setInterestedPiece(BitField bf){
		for(int i = 0; i < totPieces; i++){
			if((bitPieceIndex[i] == false) && (bf.bitPieceIndex[i] == true)){
				bitPieceIndex[i] = true;
				countFinishedPieces++;
				if(countFinishedPieces == totPieces)
					finished = true;
				return i;
			}
		}
		return -1;
	}
	
}
