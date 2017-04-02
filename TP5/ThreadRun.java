import org.omg.CORBA.* ;
import org.omg.PortableServer.* ;
import java.util.* ;
import java.io.* ;
import org.omg.CosNaming.* ;

class ThreadRun
  extends Thread
{
  private ORB orb ;
  public ThreadRun(ORB orb)
  {
    this.orb = orb ;
  }
  
  public void run()
  {
    try
    {
      orb.run() ;
    }
    catch (Exception e)
    {
      System.out.println("ERROR : " + e) ;
      e.printStackTrace(System.out) ;
      System.exit(1) ;
    } 
  }
  
  public void shutdown()
  {
    orb.shutdown(false) ;
  }
}
