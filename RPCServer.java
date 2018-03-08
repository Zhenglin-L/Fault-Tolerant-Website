package myPackage;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
/**
 *  The server class deals with handling request from the client.
 *  It is implemented under the mechanism of UDP communication.
 */
public class RPCServer {
	private static final int N = 4;
    private static final int W = 3;
    private static final int F = 1;
    private static final int WQ = W-F;
    private static final int R = WQ;
    private static final int portProj1bRPC = 5300;
    private static final int operationSESSIONREAD = 0;
    private static final int operationSESSIONWRITE = 1;
    private static final int maxPacketSize = 512;
    private String currentServerID = null;
    DatagramSocket rpcSocket;
    
    public RPCServer() throws Exception{
    	rpcSocket = new DatagramSocket(portProj1bRPC);
    }
    public void handleRequest() throws Exception{
    	while(true){
    		byte[] inBuf = new byte[maxPacketSize];
    		DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
    		rpcSocket.receive(recvPkt);
    		InetAddress returnAddr = recvPkt.getAddress();
    		int returnPort = recvPkt.getPort();
    		// the format of the call message of session read is callID+operationCode+sessionID+version
    		String recvData = new String(recvPkt.getData(), 0, recvPkt.getLength());
    		String[] tokens = recvData.split("@");
    		if(tokens.length!=4 && tokens.length!=5 && tokens.length!=6){
    			//4->session read   5->session read+logout   6->session write
    			throw new Exception("The received message is not complete!");
    		}
    		String operationCode = tokens[1];
    		String sessionID = tokens[2];
    		int version = Integer.parseInt(tokens[3]);
    		byte[] outBuf = new byte[maxPacketSize];
    		String sendData = null;
    		switch(Integer.parseInt(operationCode)){
    		    case operationSESSIONREAD:
    		    	boolean flag;
    		    	SessionData sessionData = SessionData.retrieveSessionData(sessionID, version);
    		    	if(tokens.length == 5 && Boolean.parseBoolean(tokens[4]) == true){
    		    		//set the discard_time to current time, so that it can be garbage collected right away
    		    		if(sessionData != null) sessionData.setExpirationTime(Calendar.getInstance().getTime());
    		    		sendData = tokens[0];
    		    	}
    		    	else{
    		    	    if(sessionData != null){
    		    		    flag = true;
    		    	        //the required session state exists, the format of the send data is callID+flag+message
    		    		    if(currentServerID == null) currentServerID = UtilityMethods.retrieveServerID();
    		    	        sendData = tokens[0]+"@"+flag+"@"+sessionData.getMessage()+"@"+currentServerID;
    		    	    }
    		    	    else{
    		    		    flag = false;
    		    		    //the required session data was not found on the session table, two possible reasons, one is 
    		    		    //that the server has timed out, the other is that the session is not expired, but due to a possible 
    		    		    //server reboot, the required session data may be lost.
    		    		    //the format of the send data is callID+flag
    		    		    sendData = tokens[0]+"@"+flag;
    		    	    }
    		    	}
    		    	if(sendData.getBytes().length<=maxPacketSize){
    		    		outBuf = sendData.getBytes();
    		    	}
    		    	else{
    		    		throw new Exception("The data is too large to be fit in a single UDP packet.");
    		    	}   		    	
    			    break;
    			    
    		    case operationSESSIONWRITE:
    		    	String message = tokens[4];
    		    	SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
    		    	Date expirationTime = format.parse(tokens[5]);
    		        SessionData newSessionData = new SessionData(sessionID, version, message, expirationTime);
    		        SessionData.insertNewSessionData(newSessionData);
    		        //send serverID back to client, the format is callID+server ID
    		        if(currentServerID == null) currentServerID = UtilityMethods.retrieveServerID();
    		        sendData = tokens[0]+"@"+currentServerID;
    		        if(sendData.getBytes().length<=maxPacketSize){
    		        	outBuf = sendData.getBytes();
    		        }
    		        else{
    		        	throw new Exception("The data is too large to be fit in a single UDP packet.");
    		        }
    		        break;		    	
    		}
    		DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, returnAddr, returnPort);
    		System.out.println("sending response back to client!");
    		rpcSocket.send(sendPkt);
    	}
    }
}
