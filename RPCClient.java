package myPackage;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Date;
import java.io.*;
/**
 *  This class provides two methods of session read
 *  and session write. They are all based on the mechanism
 *  of UDP communication.
 */
public class RPCClient {
	private static final int N = 4;
    private static final int W = 3;
    private static final int F = 1;
    private static final int WQ = W-F;
    private static final int R = WQ;
    private static final int portProj1bRPC = 5300;
    private static final int operationSESSIONREAD = 0;
    private static final int operationSESSIONWRITE = 1;
    private static final int maxPacketSize = 512;
    private int rpc_counter = 0;
    private static int getIpAddrCode = 0;
    private static int SESSION_TIMEDOUT = 0;
    private static int SERVER_FAILED = 1;
    private static int SUCCESS = 2;
    private static final int LOGOUT_SUCCESS = 3;
    private static Map<String, String> map = new HashMap<String, String>();
    /* contains logic that deals with sending session read request and receiving its reply message */
    public ReturnedMessage sessionReadClient(String sessionID, int version, String[] serverIDs, boolean logout){
    	DatagramSocket rpcSocket = null;
    	String callID = null;
    	String sendData = null;
    	try{
            rpcSocket = new DatagramSocket();	
            byte[] outBuf = new byte[maxPacketSize];
            callID = ""+(++rpc_counter);
            if(logout == true){ //the special version of session read that deals with logout operation
            	sendData = callID+"@"+operationSESSIONREAD+"@"+sessionID+"@"+version+"@"+logout;
            }
            else{
                sendData = callID+"@"+operationSESSIONREAD+"@"+sessionID+"@"+version;
            }
            if(sendData.getBytes().length<=maxPacketSize){
                outBuf = sendData.getBytes();
            } 
            else{
                System.out.println("The send data is too large to fit in a single UDP packet.");
            }
            //R == WQ, send read requests to WQ servers
            for(String serverID:serverIDs){
            	if(!map.containsKey(serverID)){ //use a hash map to cache the result
            		String destAddr = UtilityMethods.readFromFile(serverID, getIpAddrCode);
            		map.put(serverID, destAddr);
            	}
                DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, InetAddress.getByName(map.get(serverID)), portProj1bRPC);
        	    rpcSocket.setSoTimeout(2000);
        	    rpcSocket.send(sendPkt);
            } 
    	} catch(Exception e){
    		    e.printStackTrace();
    	}
        byte[] inBuf = new byte[maxPacketSize];
        DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
        String recvData = null;
        String[] tokens = null;
        ReturnedMessage returnedMessage = null;
        int resp_cnt = 0;
        //if all the reply message shows the data was not found(flag is false), then the required session has timed out
        try{
            do{
            	if(resp_cnt == R) break;
            	recvPkt.setLength(inBuf.length);
            	rpcSocket.receive(recvPkt);
            	recvData = new String(recvPkt.getData(), 0, recvPkt.getLength());
            	tokens = recvData.split("@");
            	resp_cnt = resp_cnt+1;
            }
            while(!tokens[0].equals(callID) || (tokens.length !=1  && tokens[0].equals(callID) && Boolean.parseBoolean(tokens[1]) == false));
            if(tokens.length == 1){
            	returnedMessage = new ReturnedMessage(LOGOUT_SUCCESS, null, null, null);
            }
            else{
                boolean flag = Boolean.parseBoolean(tokens[1]);
                if(flag){
             	    //the flag is true, it contains sessionData
            	    String message = tokens[2];
            	    String serverID =tokens[3];
            	    returnedMessage = new ReturnedMessage(SUCCESS, message, null, serverID);
                }
                else{
            	    //the flag is false, the requested session state has timed out
            	    returnedMessage = new ReturnedMessage(SESSION_TIMEDOUT, null, null, null);
                }
            }
        } catch(SocketTimeoutException stoe) { //the reason for this exception is that all the servers have failed
        	recvPkt = null;
        	returnedMessage = new ReturnedMessage(SERVER_FAILED, null, null, null);
        } catch (IOException ioe) {
        	ioe.printStackTrace();
        }
        rpcSocket.close();
        return returnedMessage;
    }
    /* contains logic that deals with sending session write request and receiving its reply message */
    public ReturnedMessage sessionWriteClient(String sessionID, int version, String message, Date discard_time){
        DatagramSocket rpcSocket = null;
    	String callID = null;
    	try{
            rpcSocket = new DatagramSocket();
            callID = ""+(++rpc_counter);
            byte[] outBuf = new byte[maxPacketSize];
            //specify the format of rpc call message of session write
            String sendData = callID+"@"+operationSESSIONWRITE+"@"+sessionID+"@"+version+"@"+message+"@"+discard_time;
            if(sendData.getBytes().length<=maxPacketSize){
        	    outBuf = sendData.getBytes();
            }
            else{
        	    System.out.println("The send data is too large to fit in a single packet.");
            }
            //randomly pick W servers out of N servers to send rpc request, since N-W = 1, we only have to pick one out of W servers randomly
            Random rand = new Random();
            int value = rand.nextInt(N);
            List<String> chosenServerIDs = new ArrayList<String>();
            for(int i=0;i<N;i++){
        	    if(i == value) continue;
        	    chosenServerIDs.add(""+i);
            }
            for(String serverID:chosenServerIDs){
            	if(!map.containsKey(serverID)){ 
            		String destAddr = UtilityMethods.readFromFile(serverID, getIpAddrCode);
            		map.put(serverID, destAddr);
            	}
        	    DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length, InetAddress.getByName(map.get(serverID)), portProj1bRPC);
        	    rpcSocket.setSoTimeout(2000);
        	    rpcSocket.send(sendPkt);
            }
    	} catch(Exception e){
    		    e.printStackTrace();
    	}
        byte[] inBuf = new byte[maxPacketSize];
        DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
        String recvData = null;
        ReturnedMessage returnedMessage = null;
        List<String> returnedServerIds = new ArrayList<String>();
        //keep a counter to keep track of the number of responses received
        int resp_cnt = 0;
        try{
        	do{
        	    recvPkt.setLength(inBuf.length);
        	    rpcSocket.receive(recvPkt);
        	    recvData = new String(recvPkt.getData(), 0 ,recvPkt.getLength());
        	    String[] tokens = recvData.split("@");
        	    if(!tokens[0].equals(callID)) continue;
        	    else resp_cnt = resp_cnt+1;
        	    returnedServerIds.add(tokens[1]);
        	}
        	while(resp_cnt<WQ);
        	String locationMetadata = "";
    	    for(String serverId:returnedServerIds){
    		    if(returnedServerIds.indexOf(serverId) == returnedServerIds.size()-1){
    			    locationMetadata = locationMetadata+serverId;
    		    }
    		    else{
    		        locationMetadata = locationMetadata+serverId+"#";
    		    }
    	    }
        	returnedMessage = new ReturnedMessage(SUCCESS, null, locationMetadata, null);
        } catch(SocketTimeoutException ste) {
        	recvPkt = null;
        	returnedMessage = new ReturnedMessage(SERVER_FAILED, null, null, null);
        } catch(IOException ioe){
        	ioe.printStackTrace();
        }
        rpcSocket.close();
        return returnedMessage;
    }
    /* a static nested class to encapsulate the fields and accessor methods of the returned message */
    public static class ReturnedMessage{
    	private int statusCode;
    	private String message;
    	private String locationMetadata;
    	private String serverID;
    	
    	private ReturnedMessage(int statusCode, String message, String locationMetadata, String serverID){
    		this.statusCode = statusCode;
    		this.message = message;
    		this.locationMetadata = locationMetadata;
    		this.serverID = serverID;  //the serverID where the session data was found
    	}
    	public int getStatusCode(){
    		return this.statusCode;
    	}
    	public String getMessage(){
    		return this.message;
    	}
    	public String getLocationMetadata(){
    		return this.locationMetadata;
    	}  	
    	public String getServerID(){
    		return this.serverID;
    	}
    }
}
