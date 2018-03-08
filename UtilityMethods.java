package myPackage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;

import myPackage.RPCClient.ReturnedMessage;
import myPackage.RPCClient;
/**
 * This class encapsulates some important methods that are used by other classes,
 * with a goal to make the code more usable and easy to debug.
 */
public class UtilityMethods {
	private static int getIpAddrCode = 0;
	private static int getRebootNumCode = 1;
	private static int SESSION_TIMEOUT_SECS = 300;
	private static int INCREMENT = 1;
	private static int SESSION_TIMEDOUT = 0;
    private static final int SERVER_FAILED = 1;
    private static final int SUCCESS = 2;
    private static final int LOGOUT_SUCCESS = 3;
    private static final int REFRESH = 0;
    private static final int REPLACE = 1;
    private static final int LOGOUT = 2;
    private static final String NETID = "xw395";
    
    /* read required data from the local file*/
    public static String readFromFile(String serverID, int operationCode){
    	String path = null;
    	String ipAddress = null;
    	String rebootNum = null;
    	String line = null;
    	if(operationCode == getIpAddrCode) path = "/metadata.txt";
    	if(operationCode == getRebootNumCode) path = "/rebootNum.txt";
    	File file = new File(path);
    	try{
    		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
    		while((line = bufferedReader.readLine())!=null){
    			if(operationCode == getIpAddrCode){
    				String[] tokens = line.split(" ");
    				if(tokens[0].equals(serverID) && operationCode == getIpAddrCode){
    					ipAddress = tokens[1].replace("\"", "");
    					System.out.println("The ip address is "+ipAddress);		
    					break;
    				}
    			}
    			if(operationCode == getRebootNumCode){
    				rebootNum = line;
    			}
    		}
    		bufferedReader.close();
    	} 
    	catch(FileNotFoundException e){
    		System.out.println("Unable to open file "+file.getName());
    	} 
    	catch(IOException e){
    		e.printStackTrace();
    	}
    	if(operationCode == getIpAddrCode) return ipAddress;
        return rebootNum;
    }
    /* retrieve the current server's ami-launch-index, which is also the serverID */
	public static String retrieveServerID(){
		String serverID = null;
		String inputLine = null;
		try{
		    URL ec2MetaData = new URL("http://169.254.169.254/latest/meta-data/ami-launch-index");
		    URLConnection ec2Connection = ec2MetaData.openConnection();
		    BufferedReader in = new BufferedReader(new InputStreamReader(ec2Connection.getInputStream()));
		    while((inputLine = in.readLine())!=null){
			    serverID = inputLine;
		    }
		    in.close();
		} catch(Exception e){
			e.printStackTrace();
		}
		return serverID;
	}
	/* encapsulate servlet logic about issuing session read and session write requests */
	public static Map<String, Object> processCookieUserRequest(String sessionID, int version, String locationMetadata, RPCClient rpcClient, int operationCode, String newMessage){
		Map<String, Object> map = new HashMap<String, Object>();
    	String[] serverIDs = locationMetadata.split("#");
    	ReturnedMessage returnedReadMessage = null;
    	ReturnedMessage returnedWriteMessage = null;
    	String message = null;
    	Date expirationTime = null;
		//before issue session read request, first need to check if the required session state is in the session table of the local server
    	SessionData retrieved = SessionData.retrieveSessionData(sessionID, version);
    	if(operationCode == LOGOUT){
    		if(retrieved!=null){
    			//set the discard time of the session state to be current time
    			retrieved.setExpirationTime(Calendar.getInstance().getTime());
    		}
    		//send a special version of session read request regarding logout operation
    		returnedReadMessage = rpcClient.sessionReadClient(sessionID, version, serverIDs, true);
    		if(returnedReadMessage.getStatusCode() == LOGOUT_SUCCESS){
    		    map.put("returnedReadMessage", returnedReadMessage);
    		}
    	}
    	else{
    		if(retrieved!=null){ //the required session has not timed out			
    			//session ID remains the same, only increment version number and update discard time
    			version = version+1;
    			Calendar now = Calendar.getInstance();
    			now.add(Calendar.SECOND, SESSION_TIMEOUT_SECS+INCREMENT);
    			expirationTime = now.getTime();
    			switch(operationCode){
    			case REFRESH:
    				message = retrieved.getMessage();
    				break;
    			case REPLACE:
    				message = newMessage;
    				break;
    			}			
    		}
    		else{    	
    			//issue session read request to see if the session state is expired or not
    			returnedReadMessage = rpcClient.sessionReadClient(sessionID, version, serverIDs, false);		
    			if(returnedReadMessage.getStatusCode() == SESSION_TIMEDOUT){
    				//the required session has timed out, therefore the sessionID needs to be changed by incrementing session_num
    				SessionData sessionData = SessionData.createNewSessionDataByDefault(++SessionServlet.session_num);
    				sessionID = sessionData.getSessionID();
    				version = sessionData.getVersion();
    				expirationTime = sessionData.getExpirationTime();
    				switch(operationCode){
    				case REFRESH:
    					message = sessionData.getMessage();
    					break;
    				case REPLACE:
    					message = newMessage;
    					break;
    				}
    			}
    			if(returnedReadMessage.getStatusCode() == SUCCESS){
    				//the required session has not timed out, session ID remains the same, increment version number and update discard time
    				version = version+1;	
    				Calendar now = Calendar.getInstance();
    				now.add(Calendar.SECOND, SESSION_TIMEOUT_SECS+INCREMENT);
    				expirationTime = now.getTime();
    				switch(operationCode){
    				case REFRESH:
    					message = returnedReadMessage.getMessage();
    					break;
    				case REPLACE:
    					message = newMessage;
    					break;
    				}
    			}
    			if(returnedReadMessage.getStatusCode() == SERVER_FAILED){
    				//all the servers have failed
    				map.put("returnedReadMessage", returnedReadMessage);	
    				return map;
    			}
    		}
    		//send session write requests to write out the updated session data
    		returnedWriteMessage = rpcClient.sessionWriteClient(sessionID, version, message, expirationTime);
    		locationMetadata = returnedWriteMessage.getLocationMetadata();
    		map.put("sessionID", sessionID);
    		map.put("version", version);
    		map.put("message", message);
    		map.put("expirationTime", expirationTime);
    		map.put("locationMetadata", locationMetadata);
    		map.put("returnedReadMessage", returnedReadMessage);
    		map.put("returnedWriteMessage", returnedWriteMessage);
    	}
    	return map;
	}
	/* encapsulate the operations of setting certain attributes of the request and cookie objects */
	public static void generateResponse(HttpServletRequest request, HttpServletResponse response, Object sessionID, Object version, Object message, Object locationMetadata, String currentServerID, String currentRebootNum, String foundServerID){
		Calendar now = Calendar.getInstance();
		Date creationTime = now.getTime();
		now.add(Calendar.SECOND, SESSION_TIMEOUT_SECS);
		Date expirationTime = now.getTime();
		sessionID = (String)sessionID;
		version = (int)version;
		locationMetadata = (String)locationMetadata;
	    request.setAttribute("sessionID", sessionID);
	    request.setAttribute("version", version);
	    request.setAttribute("message", (String)message);
        request.setAttribute("expirationTime", expirationTime);
        request.setAttribute("creationTime", (Date)creationTime);
        request.setAttribute("serverID", currentServerID);
        request.setAttribute("rebootNum", currentRebootNum);
        request.setAttribute("foundServerID", foundServerID);
    
        Cookie newCookie = new Cookie("CS5300PROJ1SESSION", sessionID+"@"+version+"@"+locationMetadata);
        newCookie.setMaxAge(SESSION_TIMEOUT_SECS);
        newCookie.setPath("/");
        //using the big data system
        newCookie.setDomain("."+NETID+".bigdata.systems");
        request.setAttribute("sessionCookie", newCookie.getValue());
        request.setAttribute("cookieDomain", newCookie.getDomain());
        response.addCookie(newCookie);
        //disable caching
        response.setHeader("Cache-Control", "private, no-store, no-cache, must-revalidate");
	}
}
