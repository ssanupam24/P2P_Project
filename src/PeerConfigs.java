/*
 *@author Gloria 
 * This class is used to read the Peer Config and Common Config file and store the information in 
 * respective member variables
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.io.*;

public class PeerConfigs {
	private FileInputStream in = null;
	private int prefNeighbors;
	private int timeUnchoke;
	private int timeOptUnchoke;
	private String fileName;
	private int sizeOfFile;
	private int sizeOfPiece;
	private int sizeOfLastPiece;
	private int totalPieces;
	private int totalPeers;
	private int totalPeersWithEntireFile = 0;
	private ArrayList<Integer> peerList;
	private ArrayList<String> hostList;
	private ArrayList<Integer> downloadPortList;
	private ArrayList<Integer> uploadPortList;
	private ArrayList<Boolean> hasWholeFile;
	private ArrayList<Integer> havePortList;
	
	public PeerConfigs() throws Exception
	{
		String path = System.getProperty("user.home") + "/project/"; 
		readCommonConfig(path + "Common.cfg");
		readPeerInfoConfig(path + "PeerInfo.cfg");
		//printCommonSettings();
		//printPeerInfo(totalPeers);
	}
	
	public int getPrefNeighbors(){
		 return prefNeighbors;
	}
	public int getTotalPeersWithEntireFile(){
		return totalPeersWithEntireFile;
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
	/*
	 * This function reads the Common.cfg file and populates the corresponding variables
	 * @param The path of the Common.cfg file	
	 */
	public void readCommonConfig(String commonConfigPath) throws Exception
	{
		try{
			in = new FileInputStream(commonConfigPath);
			Scanner scanner = new Scanner(in);
			
			String token = "";
			
			while(scanner.hasNext())
			{
				token = scanner.next();
				if(token.compareToIgnoreCase("NumberOfPreferredNeighbors") == 0)
				{
					if(!scanner.hasNextInt())
						throw new Exception("Error:  Integer value required for the number of preferred neighbors.");
					else
						prefNeighbors = scanner.nextInt();
				}
				else if(token.compareToIgnoreCase("UnchokingInterval") == 0)
				{
					if(!scanner.hasNextInt())
						throw new Exception("Error:  Integer value required for the preferred neighbor selection interval.");
					else
						timeUnchoke = scanner.nextInt();
				}
				else if(token.compareToIgnoreCase("OptimisticUnchokingInterval") == 0)
				{
					if(!scanner.hasNextInt())
						throw new Exception("Error:  Integer value required for the optimistically unchoked neighbor selection interval.");
					else
						timeOptUnchoke = scanner.nextInt();
				}
				else if(token.compareToIgnoreCase("FileName") == 0)
				{
					if(!scanner.hasNext())
						throw new Exception("Error:  File name not correctly specified.");
					else
						fileName = scanner.next(); 
				}
				else if(token.compareToIgnoreCase("FileSize") == 0)
				{
					if(!scanner.hasNextInt())
						throw new Exception("Error:  Integer value required for the file size.");
					else
						sizeOfFile = scanner.nextInt();
				}	
				else if(token.compareToIgnoreCase("PieceSize") == 0)
				{
					if(!scanner.hasNextInt())
						throw new Exception("Error:  Integer value required for the piece size.");
					else
						sizeOfPiece = scanner.nextInt();
				}
				else
				{
					throw new Exception("Error.  Invalid string found in the common configuration file." + token);
				}
			}		
		
			if(in != null)
				in.close();
			
			if(sizeOfFile % sizeOfPiece == 0) {
				sizeOfLastPiece = sizeOfPiece;
				totalPieces = sizeOfFile/sizeOfPiece;
			}
			else{
				sizeOfLastPiece = sizeOfFile % sizeOfPiece;
				totalPieces = sizeOfFile/sizeOfPiece + 1;
			}
		}
		catch(IOException exception)
		{
			exception.printStackTrace();
		}
	}
	/*
	 * This function reads the PeerInfo.cfg file and populates the corresponding variables
	 * @param The path of the PeerInfo.cfg file
	 */
	public void readPeerInfoConfig(String peerConfigPath) throws Exception
	{
		try{
			peerList = new ArrayList<Integer>();
			hostList = new ArrayList<String>();
			downloadPortList = new ArrayList<Integer>();
			uploadPortList = new ArrayList<Integer>();
			hasWholeFile = new ArrayList<Boolean>();
			havePortList = new ArrayList<Integer>();
			
			
			in = new FileInputStream(peerConfigPath);
			Scanner scanner = new Scanner(in);

			int tokenNum = 0;
			int lineNum = 0;
			int modResult;
			
			while(scanner.hasNext())
			{
				++tokenNum;
	            lineNum = tokenNum/4;
	            
	            modResult = tokenNum % 4;
	            
         	   if(modResult == 0) // token indicates whether the peer has the whole file
         	   {
         		   int wholeFile;
         		   ++totalPeers;
         		   
         		   if(scanner.hasNextInt())
         		   {
         			   wholeFile = scanner.nextInt();
         			   if(wholeFile == 1) {
         				   hasWholeFile.add(true);
         				   totalPeersWithEntireFile++;
         			   }
         			   else if(wholeFile == 0) {
         				   hasWholeFile.add(false);
         			   }
         		   }
         		   else
         			   throw new Exception("Error.  You must specify either a 0 or 1 to denote if the peer has the entire file.");
         	   }
         	   else if(modResult == 1) // token is the peer ID found in the current line of the file
         	   {   
        		   if(scanner.hasNextInt())
         		   {
            		   lineNum = (tokenNum-1)/4;	            		   
            		   peerList.add(lineNum, scanner.nextInt());	
         		   }  
        		   else
        			   throw new Exception("Error.  You must specify the peer id as an integer.");
         	   }
         	   else if(modResult == 2) // token is the host name/IP address found in the current line of the file
         	   {
        		   lineNum = (tokenNum-1)/4;
        		   hostList.add(lineNum, scanner.next());
         	   }	 
        	   else if(modResult == 3) // token is the port number found in the current line of the file
        	   {
        		   
        		   if(scanner.hasNextInt())
        		   {
        			   int portNum = scanner.nextInt();
        			   
            		   if(portNum < 0 || portNum > 65535)
            		   {
            			   throw new Exception("Error.  Invalid port specified in the PeerInfo configuration file.");   
            		   }
            		   
            		   downloadPortList.add(lineNum, portNum);
            		   uploadPortList.add(lineNum, portNum + 1);
            		   havePortList.add(lineNum, portNum + 2);
        		   }
        		   else
        			   throw new Exception("Error.  You must specify the port # as an integer.");  
        	   }	           
			}
			
			if(in != null)
				in.close();
		}
		catch(IOException exception)
		{
			exception.printStackTrace();
		}
		
	}
	
	// print info read from Common.cfg
	public void printCommonSettings()
	{
		System.out.printf("# Preferred neighbors: %d\nUnchoking Interval: %d\n"
				+ "Optimistic Unchoking Interval: %d\n"
				+ "File Name: %s\nFile size: %d\nPiece size: %d\n", prefNeighbors,
				timeUnchoke, timeOptUnchoke, fileName, sizeOfFile, sizeOfPiece);
	}
	
	// print info read from PeerInfo.cfg
	public void printPeerInfo(int totalPeers)
	{
		int i = 0;
		
		System.out.printf("\nTotal # peers: %d\n", totalPeers);
		System.out.println("\nPeer List:");
		
		int wholeFile = 0;
		
		while(i < totalPeers)
		{
			if(hasWholeFile.get(i) == true)
				wholeFile = 1;
			else
				wholeFile = 0;
			
			System.out.printf("\nPeer #: %d  Host Name: %s  UploadPort: %d  DownloadPort: %d  "
					+ "hasWholeFile: %d  HavePortList: %d", peerList.get(i), hostList.get(i),
			downloadPortList.get(i), uploadPortList.get(i), wholeFile, havePortList.get(i));
			++i;
		}
	}
	
	public int getPeerList(int index)
	{
		return peerList.get(index);
	}
	
	public String getHostList(int index)
	{
		return hostList.get(index);
	}
	
	public int getDownloadPortList(int index)
	{
		return downloadPortList.get(index);
	}
	
	public int getUploadPortList(int index)
	{
		return uploadPortList.get(index);
	}
	
	public int getHavePortList(int index)
	{
		return havePortList.get(index);
	}
	
	
	public boolean getHasWholeFile(int index)
	{
		return hasWholeFile.get(index);
	}

}
