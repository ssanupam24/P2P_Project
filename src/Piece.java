
public class Piece {
	private final byte[] pieceContent;
	private final int pieceNum;
	
	public Piece(byte[] pieceContent, int pieceNum){
		this.pieceContent = pieceContent;
		this.pieceNum = pieceNum;
	}
	public byte[] getPieceContent(){
		return pieceContent;
	}
	public int getPieceNum(){
		return pieceNum;
	}
}
