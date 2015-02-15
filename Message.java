public class Message 
{
  public byte type;  // parse byte & get message type
  public int length;  // length of message in bytes
  public byte [] payload;
  
  public byte choke = 0;
  public byte unchoke = 1;
  public byte interested = 2;
  public byte notInterested = 3;
  public byte have = 4;
  public byte bitfield = 5;
  public byte request = 6;
  public byte piece = 7;
  public byte finish = 8; 
  
  public int getLength()
  {
    return length;
  }
  
  public byte getType()
  {
    return type;
  }
  
  public byte [] getPayload()
  {
    return payload;
  }
  
  public void setLength(int length)
  {
    this.length = length;
  }
  
  public void setType(byte type)
  {
    this.type = type;
  }
  
  
  public void setPayload()
  {
	  switch (type)
	  {
  	    case 0: // choke message
		  // insert code here
  	    	
	    case 1: // unchoke message
			  // insert code here
	    	
	    case 2: // interested message
			  // insert code here
	    	
	    case 3: // not interested message
			  // insert code here
	    	
	    case 4: // have message
			  // insert code here
	    	
	    case 5: // bitfield message
			  // insert code here
	    	
	    case 6: // request message
			  // insert code here
	    	
	    case 7: // piece message
			  // insert code here
	    	
	    case 8: // finish message
			  // insert code here
	    	
	    default:    
	    	// insert code here, if needed
    }
  }
  
  // return value indicates if message transmission successful
  public void sendMessage()
  {
    // insert code here
  }
  
  public void receiveMessage()
  {
    // insert code here 
  }
  
}
