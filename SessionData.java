package myPackage;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.UUID;
/**
 * This class encapsulates the required information describing the session's state,
 * including session ID, version number, message and expiration time. The class also
 * provides some operations regarding the interaction(insertion, retrieval and garbage collection) with the session data table.
 * The session table is instantiated as a concurrent hash map with inherent mechanism to handle concurrency.
 */
public class SessionData {
	private SessionTableKey key;
    private String message;
    private Date expirationTime;
    public static Map<SessionTableKey, SessionData> sessionDataTable = new ConcurrentHashMap<SessionTableKey, SessionData>(new HashMap<SessionTableKey, SessionData>());
    private static final int SESSION_TIMEOUT_SECS = 300;
    private static final int INCREMENT = 1;
    private static int getRebootNumCode = 1;
    private static String serverID = null;
    private static String rebootNum = null;
    
    public SessionData(String sessionID, int version, String message, Date expirationTime){
    	this.key = new SessionTableKey(sessionID, version);
    	this.message = message;
    	this.expirationTime = expirationTime;
    }

    public String getSessionID(){
    	return this.key.getSessionID();
    }
    
    public void setVersion(int newVersion){
    	this.key.setVersion(newVersion);
    }
    
    public int getVersion(){
    	return this.key.getVersion();
    }
    public SessionTableKey getKey(){
    	return this.key;
    }
    public void setMessage(String newMessage){
    	this.message = newMessage;
    }
    
    public String getMessage(){
    	return this.message;
    }
    
    public void setExpirationTime(Date newExpirationTime){
    	this.expirationTime  = newExpirationTime;
    }
    
    public Date getExpirationTime(){
    	return this.expirationTime;
    }
    public String toString(){
    	return key.getSessionID()+"#"+key.getVersion()+"#"+getMessage()+"#"+getExpirationTime();
    }
    /* create a new instance of a session state by default */
    public static SessionData createNewSessionDataByDefault(int sessionNum){
    	try{
    		if(serverID == null) serverID = UtilityMethods.retrieveServerID();
    	    rebootNum = UtilityMethods.readFromFile(serverID, getRebootNumCode);
    	} catch(Exception e){
    		e.printStackTrace();
    	}
    	String sessionID = serverID+"-"+rebootNum+"-"+sessionNum;
    	int version = 0;
    	String message = "Hello, User!";
    	Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, SESSION_TIMEOUT_SECS+INCREMENT);
		Date expirationTime = calendar.getTime();
		return new SessionData(sessionID, version, message, expirationTime);
    }
    
    /* retrieve the session data from the table */
    public static SessionData retrieveSessionData(String sessionID, int version){
    	System.out.println("Starting to retrieve data!");
        return sessionDataTable.get(new SessionTableKey(sessionID, version));
    }
    /* insert the new session data into the table */
    public static void insertNewSessionData(SessionData sessionData){
    	sessionDataTable.put(sessionData.getKey(), sessionData);
    }
    /* remove the session data from the table */
    public static SessionData removeSessionData(String sessionID, int version){
    	return sessionDataTable.remove(new SessionTableKey(sessionID, version));
    }
    public static void tableCleanUp(){
    	for(SessionTableKey key : sessionDataTable.keySet()){
            if(sessionDataTable.get(key).getExpirationTime().before(Calendar.getInstance().getTime())){
            	sessionDataTable.remove(key.getSessionID());
    		}
    	}
    }
    /* A static nested class to encapsulate the sessionID and version number. They two together forms the primary
     key of the sessionDataTable. */
    public static class SessionTableKey{
    	private String sessionID;
    	private int version;
    	private SessionTableKey(String sessionID, int version){
    		this.sessionID  = sessionID;
    		this.version = version;
    	}
    	public void setSessionID(String sessionID){
    		this.sessionID = sessionID;
    	}
    	public String getSessionID(){
    		return this.sessionID;
    	}
    	public void setVersion(int version){
    		this.version = version;
    	}
    	public int getVersion(){
    		return this.version;
    	}
    	@Override
    	public boolean equals(Object key){
    		if(key instanceof SessionTableKey){
    		    SessionTableKey tmp = (SessionTableKey)key;
    		    if(tmp.getSessionID().equals(sessionID) && tmp.getVersion() == version){
    			    return true;
    		    }
    		    else{
    		    	return false;
    		    }
    	    }
    		return false;
    	}
    	@Override
    	public int hashCode(){
    		return sessionID.hashCode()+version;
    	}   	   	
    }
}
