import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.Producer;
import Gimme.Coordinator;
import Gimme.CoordinatorHelper;
import Gimme.ProducerPOA;
import Gimme.ProducerHelper;

public class ProducerImpl extends ProducerPOA {
    Producer myprod;
    Coordinator coordinator;

    public int getResource(int amount){ return 0;}
    public int queryAmount(){
        System.out.println("Query!!");
        return 0;}

    public static void main(String args[]){

        if (args.length != 2){
            System.out.println("Usage : java ProducerImpl" + " <machineServeurDeNoms>" + " <No Port>") ;
            return ;
        } 

        try {
            CorbaManager cm = new CorbaManager(args[0],args[1]);

            /* Create corba object */ 
            ProducerImpl producer = new ProducerImpl() ;
            producer.myprod = ProducerHelper.narrow(cm.getRef(producer)) ; 

            /* Get coordinator */
            producer.coordinator = CoordinatorHelper.narrow(cm.getRef("Coordinator"));

            producer.coordinator.loginProducer(producer.myprod);

            cm.runORB();

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
