import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.Producer;
import Gimme.Consumer;
import Gimme.Coordinator;
import Gimme.CoordinatorHelper;
import Gimme.ConsumerPOA;
import Gimme.ConsumerHelper;

public class ConsumerImpl extends ConsumerPOA {
    Consumer mycons;
    Coordinator coordinator;

    public int start(Producer p){
        return 0;
    }

    public static void main(String args[]){

        if (args.length != 2){
            System.out.println("Usage : java ConsumerImpl" + " <machineServeurDeNoms>" + " <No Port>") ;
            return ;
        } 

        try {
            // Init ORB
            String [] argv = {"-ORBInitialHost", args[0], "-ORBInitialPort", args[1]} ; 
            ORB orb = ORB.init(argv, null) ;

            // Init POA
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA")) ;
            rootpoa.the_POAManager().activate() ;

            // creer l'objet qui sera appele' depuis le serveur
            ConsumerImpl consumer = new ConsumerImpl() ;
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(consumer) ;
            consumer.mycons = ConsumerHelper.narrow(ref) ; 
            if (consumer.mycons == null) {
                System.out.println("Pb pour obtenir une ref sur le client") ;
                System.exit(1) ;
            }

            // contacter le serveur
            String reference = "corbaname::" + args[0] + ":" + args[1] + "#Coordinator" ;
            org.omg.CORBA.Object obj = orb.string_to_object(reference) ;

            // obtenir reference sur l'objet distant
            consumer.coordinator = CoordinatorHelper.narrow(obj) ;
            if (consumer.coordinator == null){
                System.out.println("Pb pour contacter le serveur") ;
                System.exit(1) ;
            } 
            /*
            // lancer l'ORB dans un thread
            client.thread = new ThreadRun(orb) ;
            client.thread.start() ;
            client.loop() ;
            */
        } catch (Exception e) {
            System.out.println("ERROR : " + e) ;
            e.printStackTrace(System.out) ;
        } finally {
        /*
          // shutdown
          if (client != null)
            client.thread.shutdown() ;
        */
        }
    }
}
