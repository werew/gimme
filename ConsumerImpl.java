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
    ThreadRun orbthread;

    public void hello(){
        System.out.println("Hello!");
    }

    public int start(Producer p){
        p.queryAmount();
        return 0;
    }
    

    public static void main(String args[]){

        ConsumerImpl consumer = null;

        if (args.length != 2){
            System.out.println("Usage : java ConsumerImpl" + " <machineServeurDeNoms>" + " <No Port>") ;
            return ;
        } 

        try {
            CorbaManager cm = new CorbaManager(args[0],args[1]);

            /* Create corba object */ 
            consumer = new ConsumerImpl() ;
            consumer.mycons = ConsumerHelper.narrow(cm.getRef(consumer)) ; 

            /* Get coordinator */
            consumer.coordinator = CoordinatorHelper.narrow(cm.getRef("Coordinator"));

            consumer.coordinator.loginConsumer(consumer.mycons);

            cm.runORB();

/*
            // lancer l'ORB dans un thread
            consumer.orbthread = new ThreadRun(orb) ;
            consumer.orbthread.start() ;
*/


        } catch (Exception e) {
            System.out.println("ERROR : " + e) ;
            e.printStackTrace(System.out) ;
        } finally {
           if (consumer != null) consumer.orbthread.shutdown() ;
        }
    }
}
