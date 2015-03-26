import java.io.*;
import java.net.Socket;


/**
 * @author Abhishek
 * This class is used to create, send and receive handshake messages through sockets
 */
 
public class HandshakeMessage {
	
	private String header = "P2PFILESHARINGPROJ";
	private final byte[] zerobits = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
	private int peerID;
	
	
	public String getHandshakeHeader(){
		return header;
	}
	
	public byte[] getZerobits(){
		return zerobits;
	}
	
	public void setPeerID(int id){
		peerID = id;
	}
	
	public int getPeerID(){
		return peerID;
	}
	
	 public void sendMessage(Socket socket){
		 try{
			OutputStream os = socket.getOutputStream();
			os.write(header.getBytes());
			os.write(zerobits);
			os.write(ByteIntConversion.intToByteArray(peerID));
		} 
		  catch (IOException e) {
			e.printStackTrace();
		}
	 }
	 
	 public void receiveMessage(Socket socket){
		 try{
			 InputStream is = socket.getInputStream();
             byte[] newHeader = new byte[18];
             byte[] newID = new byte[4];
             is.read(newHeader);
             header = new String(newHeader, "UTF-8");
             is.read(zerobits);
             is.read(newID);
             peerID = ByteIntConversion.byteArrayToInt(newID);
             System.out.println(header + " " + peerID);
		 }
		  catch (Exception e) {
				e.printStackTrace();
			}
	 }

}
