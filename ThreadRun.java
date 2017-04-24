import org.omg.CORBA.* ;
import org.omg.PortableServer.* ;
import java.util.* ;
import java.io.* ;
import org.omg.CosNaming.* ;
import java.util.concurrent.atomic.*;

class ThreadRun
  extends Thread
{
  private CorbaManager cm;
  private AtomicBoolean joinable;

  public ThreadRun(CorbaManager cm)
  {
    this.cm = cm;
    joinable = new AtomicBoolean(false);
  }
  
  public void run()
  {
    try
    {
      cm.runORB() ;
    }
    catch (Exception e)
    {
      System.out.println("ERROR : " + e) ;
      e.printStackTrace(System.out) ;
      System.exit(1) ;
    } 
  }
  
  public void shutdown(){
    cm.stop();
  }

  public synchronized void setJoinable(){
    synchronized (joinable) {
        joinable.set(true);
        joinable.notify();
    }
 }

 public void waitJoinable(){
    while (joinable.get() == false){
        synchronized (joinable) {
            try { joinable.wait(); 
            } catch (Exception e){
                e.printStackTrace(System.out);
            }
        }
    }
 }


}
