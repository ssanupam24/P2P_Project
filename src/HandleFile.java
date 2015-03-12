
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/*
 * @author Abhishek and Anupam
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
	//Write a piece in a file
	public synchronized void writeFile(Piece p) throws IOException{
		int size = fileConfig.getSizeOfPiece();
		int offset;
		//int len;
		offset = size * p.getPieceNum();
		//len = p.getPieceContent().length;
		filename.seek(offset);
		filename.write(p.getPieceContent());
	}
	//Read a piece from a file
	public synchronized Piece readFile(int id) throws IOException{
		int length;
		//Get the total length of the piece
		if(id == fileConfig.getTotalPieces() - 1)
			length = fileConfig.getSizeOfLastPiece();
		else
			length = fileConfig.getSizeOfPiece();
		int offset = fileConfig.getSizeOfPiece() * id;
		filename.seek(offset); //Shifts the pointer to the desired location
		byte[] pieceContent = new byte[length];
		filename.read(pieceContent); //It will read length bytes from the offset position
		Piece p = new Piece(pieceContent, id);
		return p;
	}
}
	 
	
