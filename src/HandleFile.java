
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/*
 * @author Anupam
 * @description: this class is responsible for file writing and file reading and fragmenting it into number of chunks 
 * depending on the chunk size.
 * 
 */

public class HandleFile{
	
	private RandomAccessFile filename;
	private PeerConfigs fileConfig;
	
	public HandleFile(int id, PeerConfigs fileConfig) throws FileNotFoundException {
		this.fileConfig = fileConfig;
		String path = System.getProperty("user.home") + "/project/peer_" + id + "/";
		File newFolder = new File(path);
		if(!newFolder.exists()){
			//Create new directory if not present
			newFolder.mkdir();
		}
		this.filename = new RandomAccessFile(path + fileConfig.getFileName(), "rw");
	}
	//Return the file pointer to close the file
	public synchronized RandomAccessFile getFile(){
		return filename;
	} 
	//Write a piece in a file
	public synchronized void writeFile(Piece p) throws IOException{
		int size = fileConfig.getSizeOfPiece();
		int offset;
		byte[] temp = p.getPieceContent();
		int len;
		offset = size * p.getPieceNum();
		len = p.getPieceContent().length;
		getFile().seek(offset);
		int i = 0;
		while(i < len){
			getFile().writeByte(temp[i]);
			i++;
		}
	}
	//Read a piece from a file for unchoke
	public synchronized Piece readFile(int id) throws IOException{
		int length;
		//Get the total length of the piece
		if(id == fileConfig.getTotalPieces() - 1)
			length = fileConfig.getSizeOfLastPiece();
		else
			length = fileConfig.getSizeOfPiece();
		int offset = fileConfig.getSizeOfPiece() * id;
		getFile().seek(offset); //Shifts the pointer to the desired location
		byte[] pieceContent = new byte[length];
		int i = 0;
		while(i < length){
			byte t = getFile().readByte();
			pieceContent[i] = t;
			i++;
		}
		//filename.read(pieceContent); //It will read length bytes from the offset position
		Piece p = new Piece(pieceContent, id);
		return p;
	}
	//Read a piece from a file for optUnchoke
		public synchronized Piece readFileForOpt(int id) throws IOException{
			int length;
			//Get the total length of the piece
			if(id == fileConfig.getTotalPieces() - 1)
				length = fileConfig.getSizeOfLastPiece();
			else
				length = fileConfig.getSizeOfPiece();
			int offset = fileConfig.getSizeOfPiece() * id;
			getFile().seek(offset); //Shifts the pointer to the desired location
			byte[] pieceContent = new byte[length];
			int i = 0;
			while(i < length){
				byte t = getFile().readByte();
				pieceContent[i] = t;
				i++;
			}
			//filename.read(pieceContent); //It will read length bytes from the offset position
			Piece p = new Piece(pieceContent, id);
			return p;
		}
}
	 
	
