import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import org.omg.CosNaming.*;
import Chat.ClientChat;
import Chat.UserInfo;
import Chat.ServeurChatPOA;
import Chat.ServeurChat;
import Chat.ServeurChatHelper;

public class ServeurChatImpl
  extends ServeurChatPOA
{
  Vector<UserInfo> chatters = new Vector<UserInfo>() ;

  synchronized private void ch_add (UserInfo u)
  {
    chatters.addElement(u) ;
  }
  
  synchronized private Enumeration ch_elts ()
  {
    return (chatters.elements()) ;
  }
  
  synchronized private int ch_size ()
  {
    return(chatters.size()) ;
  }
  
  synchronized private UserInfo ch_geti (int i)
  {
    return ((UserInfo)chatters.elementAt(i)) ;
  }

  synchronized private void ch_remi (int i)
  {
    chatters.removeElementAt(i) ;
  }

  public void login (String name, Chat.ClientChat c)
  {
    System.out.println(name + " entered") ;
    
    if (c != null && name != null)
    {
      Enumeration enume = ch_elts() ;
      UserInfo u = new UserInfo(name, c) ;
      ch_add(u) ;

      while (enume.hasMoreElements())
      {
        UserInfo u2 = (UserInfo) enume.nextElement() ;
        try
        {
          u2.chatter.receiveNewChatter(name) ;
        }
        catch (Exception e)
        {
          logout(u2.name) ;
        }  // Tolerance aux pannes
      }
    }
  }

  public void logout (String name)
  {
    if (name == null)
    {
      System.out.println("null name on logout: cannot remove chatter") ;
      return ;
    }

    UserInfo u_gone = null ;
    Enumeration enume = null ;
    
    synchronized (chatters)
    {
      for (int i = 0; i < chatters.size(); i++)
      {
        UserInfo u = (UserInfo) chatters.elementAt(i) ;
        if (u.name.equals(name))
        {
          System.out.println(name + " left") ;
          u_gone = u ;
          chatters.removeElementAt(i) ;
          enume = chatters.elements() ;
          break ;
        }
      }
    }

    if (u_gone == null || enume == null)
    {
      System.out.println("no user by name of " + name + " found: not removing chatter") ;
      return ;
    }
	
    while (enume.hasMoreElements())
    {
      UserInfo u = (UserInfo) enume.nextElement() ;
      u.chatter.receiveExitChatter(name) ;
    }
  }

  public void broadcast (String name, String message)
  {
    System.out.println(name + " requests broadcast of the following message\n# " + message) ;
    Enumeration enume = ch_elts() ;

    Boolean logged = false ;
    while (enume.hasMoreElements())
    {
      UserInfo u = (UserInfo) enume.nextElement() ;
      if (u.name.equals(name))
      {
        logged = true ;
        break ;
      }
    }

    if (!logged)
    {
      System.out.println(name + "wants to chat but is not logged") ;
      return ;
    }
    enume = ch_elts() ;

    while (enume.hasMoreElements())
    {
      UserInfo u = (UserInfo) enume.nextElement() ;
      if (!u.name.equals(name))
      {
        try
        {
          u.chatter.receiveChat(name, message) ;
        }
        catch (Exception e)
        {
          logout(u.name) ;
        }  // Tolerance aux pannes 
      }
    }
  }  

  public void chat (String myname, String hisname, String message)
  {
    System.out.println(myname + " requests to chat with " + hisname + " and sais the following\n# " + message) ;
    Enumeration enume = ch_elts() ;

    Boolean logged = false ;
    while (enume.hasMoreElements())
    {
      UserInfo u = (UserInfo) enume.nextElement() ;
      if (u.name.equals(myname))
      {
        logged = true ;
        break ;
      }
    }

    if (!logged)
    {
      System.out.println(myname + "wants to chat but is not logged") ;
      return ;
    }

    enume = ch_elts() ;
    while (enume.hasMoreElements())
    {
      UserInfo u = (UserInfo) enume.nextElement();
      if (u.name.equals(hisname))
      {
        try
        {
          u.chatter.receiveChat(myname, message) ;
        }
	      catch (Exception e)
        {
          logout(u.name) ;
        }  // Tolerance aux pannes 
      }
    }
  }    

  public String ping ()
  {
    return ("Welcome to the forum") ;
  }

  public String whoIsLogged()
  {
    String result = new String("The following people are connected:\n") ;
    Enumeration enume = ch_elts() ;

    while (enume.hasMoreElements())
    {
      UserInfo u = (UserInfo) enume.nextElement() ;
      result += u.name + "\n" ; 
    }

    return result ;
  }

  public static void main(String args[])
  {
    if (args.length != 2)
    {
      System.out.println("Usage : java ServeurChatImpl" + " <machineServeurDeNoms>" + " <No Port>") ;
      return ;
    }
    try
    {
      String [] argv = {"-ORBInitialHost", args[0], "-ORBInitialPort", args[1]} ; 
      ORB orb = ORB.init(argv, null) ;
      ServeurChatImpl helloImpl = new ServeurChatImpl() ;

      // init POA
      POA rootpoa =	POAHelper.narrow(orb.resolve_initial_references("RootPOA")) ;
      rootpoa.the_POAManager().activate() ;

      org.omg.CORBA.Object ref = rootpoa.servant_to_reference(helloImpl) ;
      ServeurChat href = ServeurChatHelper.narrow(ref) ;

      // inscription de l'objet au service de noms
      org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService") ;
      NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef) ;
      NameComponent path[] = ncRef.to_name( "ServeurChat" ) ;
      ncRef.rebind(path, href) ;

      System.out.println("ServeurChat ready and waiting ...") ;
      orb.run() ;
    }
    catch (Exception e)
    {
      System.err.println("ERROR: " + e) ;
      e.printStackTrace(System.out) ;
    }
      
    System.out.println("ServeurChat Exiting ...") ;
  }
}









