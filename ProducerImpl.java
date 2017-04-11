import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.Producer;
import Gimme.ProducerPOATie;
import Gimme.Coordinator;
import Gimme.CoordinatorHelper;
import Gimme.ProducerOperations;
import Gimme.ProducerHelper;
import Gimme.Resource;

public class ProducerImpl extends AgentImpl
implements ProducerOperations {
    Producer myprod;
    Coordinator coordinator;
    Resource resource;

    public Resource getResource(Resource request){ return null;}
    public Resource queryResource(){ return resource; }

    public static void main(String args[]){

        if (args.length != 2){
            System.out.println("Usage : java ProducerImpl" + " <machineServeurDeNoms>" + " <No Port>") ;
            return ;
        } 

        try {
            CorbaManager cm = new CorbaManager(args[0],args[1]);

            /* Create corba object */
            ProducerImpl producer = new ProducerImpl() ;
            ProducerPOATie tie = new ProducerPOATie(producer, cm.rootPOA);
            producer.myprod = tie._this(cm.orb);

            /* Get coordinator */
            producer.coordinator = CoordinatorHelper.narrow(cm.getRef("Coordinator"));

            /* Login */
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
