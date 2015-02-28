
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/*
 * @author Abhishek
 * @description: this class is responsible for file writing and file reading and fragmenting it into number of chunks 
 * depending on the chunk size.
 * 
 */

public class HandleFile{
	
	private String filename;
	private int CHUNK_SIZE;
	
	public HandleFile(String filename, int CHUNK_SIZE) {
		this.filename = "peer_" + PeerInfo.getID() + "/" + filename;
		this.CHUNK_SIZE = CHUNK_SIZE;
	}

	public ArrayList<String> readAndFragment () throws IOException
	 {
		 String filedir= this.filename;
	  File willBeRead = new File ( filedir );
	  int FILE_SIZE = (int) willBeRead.length();
	  ArrayList<String> nameList = new ArrayList<String> ();
	  int NUMBER_OF_CHUNKS = 0;
	  byte[] temporary = null;
	  
	  try {
	   InputStream inStream = null;
	   int totalBytesRead = 0;
	   
	   try {
		  inStream = new BufferedInputStream ( new FileInputStream( this.filename ));
	    
	    while ( totalBytesRead < FILE_SIZE )
	    {
	     String PART_NAME = "peer_" + PeerInfo.getID() + "/" + NUMBER_OF_CHUNKS+"";
	     int bytesRemaining = FILE_SIZE-totalBytesRead;
	     if ( bytesRemaining < CHUNK_SIZE ) // Remaining Data Part is Smaller Than CHUNK_SIZE
	                // CHUNK_SIZE is assigned to remain volume
	     {
	      CHUNK_SIZE = bytesRemaining;
	     }
	     temporary = new byte[CHUNK_SIZE]; //Temporary Byte Array
	     int bytesRead = inStream.read(temporary, 0, CHUNK_SIZE);
	     
	     if ( bytesRead > 0) // If bytes read is not empty
	     {
	      totalBytesRead += bytesRead;
	      NUMBER_OF_CHUNKS++;
	     }
	     
	     write(temporary, PART_NAME);
	    }
	    
	   }
	   finally {
	    inStream.close();
	   }
	  }
	  catch (FileNotFoundException ex)
	  {
	   ex.printStackTrace();
	  }
	  catch (IOException ex)
	  {
	   ex.printStackTrace();
	  }
	  return nameList;
	 }
	 
	 void write(byte[] DataByteArray, String DestinationFileName){
	   try {
	       OutputStream output = null;
	         output = new BufferedOutputStream(new FileOutputStream(DestinationFileName));
	         output.write( DataByteArray );
	         output.close();
	     }
	     catch(FileNotFoundException ex){
	      ex.printStackTrace();
	     }
	     catch(IOException ex){
	      ex.printStackTrace();
	     }
	 }
	 
	 /*
		 * @param: Arralist of chunk
		 * @param: Destination merged file path to store
		 * 
		 * 
		 */
	 
	 public void mergeParts ( ArrayList<String> nameList, String DESTINATION_PATH )
	 {
	 File[] file = new File[nameList.size()];
	  byte AllFilesContent[] = null;
	  
	  int TOTAL_SIZE = 0;
	  int FILE_NUMBER = nameList.size();
	  int FILE_LENGTH = 0;
	  int CURRENT_LENGTH=0;
	  
	  for ( int i=0; i<FILE_NUMBER; i++)
	  {
	   file[i] = new File (nameList.get(i));
	   TOTAL_SIZE+=file[i].length();
	  }
	  
	 
	  try {
	   AllFilesContent= new byte[TOTAL_SIZE]; // Length of All Files, Total Size
	   InputStream inStream = null;
	   
	   for ( int j=0; j<FILE_NUMBER; j++)
	   {
	    inStream = new BufferedInputStream ( new FileInputStream( file[j] ));
	    FILE_LENGTH = (int) file[j].length();
	    inStream.read(AllFilesContent, CURRENT_LENGTH, FILE_LENGTH);
	    CURRENT_LENGTH+=FILE_LENGTH;
	    inStream.close();
	   }
	   
	  }
	  catch (FileNotFoundException e)
	  {
	   System.out.println("File not found " + e);
	  }
	  catch (IOException ioe)
	  {
	    System.out.println("Exception while reading the file " + ioe);
	  }
	  finally 
	  {
	   write (AllFilesContent,DESTINATION_PATH);
	  }
	  
	 }
	 
	 public synchronized byte[] getChunk(int chunkId)
	 {
		 //check if the file exist if not then throw exception
		 //if file exist copy the whole file into temporary array
		 //return the temp array.
		 String filedir= "peer_"+ PeerInfo.getID()+"/"+chunkId+"";
		 File file= new File(filedir);
		 byte [] temp= null;
		 try {
			 InputStream istream=new BufferedInputStream(new FileInputStream(filedir));
			 int filesize= (int)file.length();
			 temp=new byte[filesize];
			 istream.read(temp);
			 istream.close();
			 
		} catch (FileNotFoundException e) {
			System.out.println("File is not found");
			e.printStackTrace();
		}
		 catch (IOException e) {
			e.printStackTrace();
		}
		 
		 return temp;
	 }
	}
	 
	
