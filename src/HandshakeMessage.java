import java.io.*;
import java.net.Socket;


/**
 * @author Abhishek
 *
 */
 
public class HandshakeMessage {
	
	private final String header = "P2PFILESHARINGPROJ";
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
			OutputStreamWriter osw = new OutputStreamWriter(os);
			BufferedWriter output = new BufferedWriter(osw);
			output.write(header);
			output.write(zerobits.toString());
			output.write(peerID);
		} 
		  catch (IOException e) {
			e.printStackTrace();
		}
		 
         
	 }
	 
	 public void receiveMessage(Socket socket){
		 try{
			 InputStream is = socket.getInputStream();
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr);
             String input = br.readLine();
             peerID = Integer.parseInt(input.substring(28));
			 
		 }
		  catch (Exception e) {
				e.printStackTrace();
			}
	 }

}
