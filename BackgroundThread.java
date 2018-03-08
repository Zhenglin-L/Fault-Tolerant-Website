package myPackage;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
/**
 *  The class is used to spawn the server thread and garbage
 *  collector thread before the servlet is invoked.
 */
public class BackgroundThread implements ServletContextListener{
	private Thread serverThread = null;
	private Thread gcThread = null;
    public void contextInitialized(ServletContextEvent sce){
        if ((serverThread == null) || (!serverThread.isAlive())){
        	serverThread = new Thread(new Runnable(){
        		@Override
        		public void run(){
        			try{
        				RPCServer rpcServer = new RPCServer();
        		        rpcServer.handleRequest();
        			} 
        			catch(Exception e){
        			    e.printStackTrace();
        			}
        		}
        	});
            serverThread.start();
        }
        if((gcThread == null) || (!gcThread.isAlive())){
        	gcThread = new Thread(new Runnable(){
        		@Override
        		public void run(){
        			while(true){
        			    try{
        			        SessionData.tableCleanUp();
        			        Thread.sleep(1000);
        			    }
        			    catch(InterruptedException e){
        				    Thread.currentThread().interrupt();
        				    e.printStackTrace();
        			    }
        			}
        		}
        	});
        	gcThread.start();
        }
    }
    public void contextDestroyed(ServletContextEvent sce){
        try {
            serverThread.interrupt();
            gcThread.interrupt();
        } 
        catch (Exception ex){
        	ex.printStackTrace();
        }
    }
}
