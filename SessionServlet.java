package myPackage;


import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import myPackage.SessionData;
import myPackage.RPCClient;
import myPackage.RPCClient.ReturnedMessage;
import myPackage.RPCServer;
import myPackage.UtilityMethods;

/**
 * Servlet implementation class SessionServlet
 * The doGet() method contains the logic to handle the situation where the client request the
 * server for the first time and the refresh button of the browser (NOT the one in the page) has
 * been clicked.
 * The doPost() method contains the different logics corresponding to different user behaviors, 
 * including clicking the "Replace", "Refresh" and "Logout" button, where the form in the jsp
 * page is submitted.
 */
@WebServlet("/SessionServlet")
public class SessionServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	//declare and initialize the configuration parameters required for the SSM protocol
    private static final int W = 3;
    private static final int F = 1;
    private static final int WQ = W-F;
    private static final int R = WQ;
    private static final int SESSION_TIMEDOUT = 0;
    private static final int SERVER_FAILED = 1;
    private static final int SUCCESS = 2;
    private static final int LOGOUT_SUCCESS = 3;
    public static final int SESSION_TIMEOUT_SECS = 300;
    public static final int INCREMENT = 1;
    private static final int REFRESH = 0;
    private static final int REPLACE = 1;
    private static final int LOGOUT = 2;
    private RPCClient rpcClient;
    public static int session_num = 0;
    private static int getRebootNumCode = 1;
    public String currentServerID = null;
    public String currentRebootNum = null;
    
    /**
     * @see HttpServlet#HttpServlet()
     */
    public SessionServlet() throws Exception{
        super();
        rpcClient = new RPCClient();
    }
    public void init(ServletConfig config) throws ServletException{
    	super.init(config); 	
    }
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if(currentServerID == null){
			currentServerID = UtilityMethods.retrieveServerID();
		}
		currentRebootNum = UtilityMethods.readFromFile(currentServerID, getRebootNumCode);
		Map<String, Object> result = null;
	    ReturnedMessage returnedWriteMessage = null;
	    ReturnedMessage returnedReadMessage = null;
	    SessionData sessionData = null;
	    List<String> cookieNames = new ArrayList<String>();
		Cookie[] cookies = request.getCookies();
		if(cookies!=null){
		    for(Cookie cookie : cookies){
			    cookieNames.add(cookie.getName());
		    }
		}
		if(cookies == null || (cookies!=null && !cookieNames.contains("CS5300PROJ1SESSION"))){
			//the client's first request or the corresponding session has timed out, therefore we need to create a new session state
			sessionData = SessionData.createNewSessionDataByDefault(++session_num);
			returnedWriteMessage = rpcClient.sessionWriteClient(sessionData.getSessionID(), sessionData.getVersion(), sessionData.getMessage(), sessionData.getExpirationTime());
		}
		else{ 
		    for(Cookie cookie : cookies){
		    	if(!cookie.getName().equals("CS5300PROJ1SESSION")) continue;
		    	String sessionID = cookie.getValue().split("@")[0];
		    	int version = Integer.parseInt(cookie.getValue().split("@")[1]);
		    	String locationMetadata = cookie.getValue().split("@")[2];
		    	cookie.setMaxAge(0);
		    	result = UtilityMethods.processCookieUserRequest(sessionID, version, locationMetadata, rpcClient, REFRESH, null);
		        returnedReadMessage = (ReturnedMessage)result.get("returnedReadMessage");
		        returnedWriteMessage = (ReturnedMessage)result.get("returnedWriteMessage");
		    }	    
		}
		if(returnedWriteMessage.getStatusCode() == SERVER_FAILED || (returnedReadMessage != null && returnedReadMessage.getStatusCode() == SERVER_FAILED)){
			getServletContext().getRequestDispatcher("/serverfailed.jsp").forward(request, response);
			return;
		}
		if(returnedWriteMessage.getStatusCode() == SUCCESS){
		    if(result!=null){
		    	if(returnedReadMessage!=null){ 
		    		//session read request was issued
			        UtilityMethods.generateResponse(request, response, result.get("sessionID"), result.get("version"), result.get("message"), result.get("locationMetadata"), currentServerID, currentRebootNum, returnedReadMessage.getServerID());
		    	}
		    	else{ 
		    		//corresponding session state was found on local server, no session read request issued
		    		UtilityMethods.generateResponse(request, response, result.get("sessionID"), result.get("version"), result.get("message"), result.get("locationMetadata"), currentServerID, currentRebootNum, currentServerID);
		    	}
			}
			else{ 
				//response corresponding to the first request or the request for the expired session state
			    UtilityMethods.generateResponse(request, response, sessionData.getSessionID(), sessionData.getVersion(), sessionData.getMessage(), returnedWriteMessage.getLocationMetadata(), currentServerID, currentRebootNum, null);
			}
		}
		getServletContext().getRequestDispatcher("/index.jsp").forward(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    Cookie[] cookies = request.getCookies();
	    List<String> cookieNames = new ArrayList<String>();
		if(cookies!=null){
		    for(Cookie cookie : cookies){
			    cookieNames.add(cookie.getName());
		    }
		}
		if(request.getParameter("replaceButton")!=null || request.getParameter("logoutButton")!=null){ 
		    if(cookieNames.contains("CS5300PROJ1SESSION")){
		    	for(Cookie cookie : cookies){
		    		if(!cookie.getName().equals("CS5300PROJ1SESSION")) continue;
		    		if(currentServerID == null) currentServerID = UtilityMethods.retrieveServerID();
		    		currentRebootNum = UtilityMethods.readFromFile(currentServerID, getRebootNumCode);
		    		String sessionID = cookie.getValue().split("@")[0];
		    		int version = Integer.parseInt(cookie.getValue().split("@")[1]);
		    		String locationMetadata = cookie.getValue().split("@")[2];
		    		String newMessage = null;
		    		ReturnedMessage returnedReadMessage = null;
		    		ReturnedMessage returnedWriteMessage = null;
		    		Map<String, Object> result = null;
		    		cookie.setMaxAge(0);
		    		if(request.getParameter("replaceButton")!=null){ //the replace button was clicked
		    			newMessage = request.getParameter("replaceText");
		    		    result = UtilityMethods.processCookieUserRequest(sessionID, version, locationMetadata, rpcClient, REPLACE, newMessage);
		    		}
		    		if(request.getParameter("logoutButton")!=null){ //the logout button was clicked
		    			result = UtilityMethods.processCookieUserRequest(sessionID, version, locationMetadata, rpcClient, LOGOUT, newMessage);
		    		}
		    		returnedReadMessage = (ReturnedMessage)(result.get("returnedReadMessage"));
	    		    returnedWriteMessage = (ReturnedMessage)(result.get("returnedWriteMessage"));
		    		if((returnedReadMessage!=null && returnedReadMessage.getStatusCode() == SERVER_FAILED) || (returnedWriteMessage!=null && returnedWriteMessage.getStatusCode() == SERVER_FAILED)){
		    			getServletContext().getRequestDispatcher("/serverfailed.jsp").forward(request, response);
		    		}
		    		else if(returnedReadMessage!=null && returnedReadMessage.getStatusCode() == LOGOUT_SUCCESS){
		    			getServletContext().getRequestDispatcher("/logout.jsp").forward(request, response);
		    		}
		    		else{
		    			if(returnedReadMessage!=null){
		    			    UtilityMethods.generateResponse(request, response, result.get("sessionID"), result.get("version"), newMessage, result.get("locationMetadata"), currentServerID, currentRebootNum, returnedReadMessage.getServerID());
			                getServletContext().getRequestDispatcher("/index.jsp").forward(request, response);     
		    			}
		    			else{ //the corresponding session state was found on current server, therefore no session read request issued
		    				UtilityMethods.generateResponse(request, response, result.get("sessionID"), result.get("version"), newMessage, result.get("locationMetadata"), currentServerID, currentRebootNum, currentServerID);
		    				getServletContext().getRequestDispatcher("/index.jsp").forward(request, response);
		    			}
		    	    }
		        }
		    }
		}
		if(request.getParameter("refreshButton")!=null){ //the refresh button was clicked, the logic in the doGet method can be reused
		    doGet(request, response);
		}
	}
}
