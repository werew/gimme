import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Chat.ClientChat;
import Chat.ServeurChat;
import Chat.ServeurChatHelper;
import Chat.ClientChatPOA;
import Chat.ClientChatHelper;

public class ClientChatImpl
  extends ClientChatPOA
{
  String my_name = "chatter" ;
  BufferedReader br = null ;
  ClientChat chatter ;
  ServeurChat serveur ;
  ThreadRun thread ;

  public void receiveNewChatter (String name)
  {
    display("# " + name + " entered") ;
  }

  public void receiveExitChatter (String name)
  {
    display("# " + name + " left") ;
  }

  public void receiveChat (String name, String message)
  {
    display(name+": "+message) ;
  }

  private void display(String s)
  {
    System.out.println(s) ;
  }

  private String getEntry()
    throws IOException
  {
    String s = null ;
    s = br.readLine() ;
    return (s) ;
  }

  private void loop()
  {
    try
    {
      boolean got_name = false ;
      String dst = null ;
      InputStreamReader isr = null ;
      String message = " " ;

      isr = new InputStreamReader (System.in) ;
      br = new BufferedReader (isr) ;
      
      while (message.equals("quit") == false)
      {
        if (!got_name)
        {
          System.out.print("Please enter your name: ") ;
          message=getEntry() ;
          got_name = true ;
          my_name = message ;
          message = " " ;
          serveur.login(my_name,chatter) ;
        }
        else
        {
          System.out.print("$ ") ;
          message=getEntry() ;
          if (message.equals("?"))
            System.out.println(serveur.whoIsLogged()) ;

          else if (message.equals("private message"))
          {
            System.out.println("Please enter destinator:") ;
            message = getEntry() ;
            dst = new String(message) ;
            message = " " ;
            System.out.println("Messages will be sent to " + dst + " from now on.") ;
          }
          else if (message.equals("public message"))
          {
            System.out.println("Messages are now public") ;
            message = " " ;
          }
          else
          {
            if (message == null) message = " " ;
            else
            {
              if (dst == null)
                serveur.broadcast(my_name,message) ;
              else
                serveur.chat(my_name,dst,message) ;
            }
          }
        }
      }
    }
    catch (IOException e)
    {
      System.out.println("ERROR : " + e) ;
      e.printStackTrace() ;
    }
    finally
    {
        if (serveur != null)
          serveur.logout(my_name) ;
    }
  }

  public static void main(String args[])
  {
    ClientChatImpl client = null ;

    if (args.length != 2)
    {
      System.out.println("Usage : java ClientChatImpl" + " <machineServeurDeNoms>" + " <No Port>") ;
      return ;
    }
    try
    {
      String [] argv = {"-ORBInitialHost", args[0], "-ORBInitialPort", args[1]} ; 
      ORB orb = ORB.init(argv, null) ;

      // Init POA
      POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA")) ;
      rootpoa.the_POAManager().activate() ;

      // creer l'objet qui sera appele' depuis le serveur
      client = new ClientChatImpl() ;
      org.omg.CORBA.Object ref = rootpoa.servant_to_reference(client) ;
      client.chatter = ClientChatHelper.narrow(ref) ; 
      if (client == null)
      {
        System.out.println("Pb pour obtenir une ref sur le client") ;
        System.exit(1) ;
      }

      // contacter le serveur
      String reference = "corbaname::" + args[0] + ":" + args[1] + "#ServeurChat" ;
      org.omg.CORBA.Object obj = orb.string_to_object(reference) ;

      // obtenir reference sur l'objet distant
      client.serveur = ServeurChatHelper.narrow(obj) ;
      if (client.serveur == null)
      {
        System.out.println("Pb pour contacter le serveur") ;
        System.exit(1) ;
      } 
      else
        System.out.println("Annonce du serveur : " + client.serveur.ping()) ;

      // lancer l'ORB dans un thread
      client.thread = new ThreadRun(orb) ;
      client.thread.start() ;
      client.loop() ;
    }
    catch (Exception e)
    {
      System.out.println("ERROR : " + e) ;
      e.printStackTrace(System.out) ;
    }
    finally
    {
      // shutdown
      if (client != null)
        client.thread.shutdown() ;
    }
  }
}