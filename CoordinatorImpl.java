import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import org.omg.CosNaming.*;
import Gimme.Consumer;
import Gimme.Producer;
import Gimme.Coordinator;
import Gimme.CoordinatorPOA;
import Gimme.CoordinatorHelper;

public class CoordinatorImpl extends CoordinatorPOA {

    private Vector<Consumer> consumers;
    private Vector<Producer> producers;

    public boolean loginConsumer(Consumer c){ return false; }
    public boolean loginProducer(Producer p){ return false; }

    public CoordinatorImpl(int maxprod, int maxcons){
        consumers = new Vector<Consumer>(maxcons);
        producers = new Vector<Producer>(maxprod);
    }

    public static void main(String args[]) {

        if (args.length != 2) {
            System.out.println("Usage : java CoordinatorImpl" + " <machineServeurDeNoms>" + " <No Port>") ;
            return ;
        }

        try {

            /* Init corba service */
            CorbaManager cm = new CorbaManager(args[0], args[1]);
           
            /* Create corba object */
            CoordinatorImpl coord = new CoordinatorImpl(10,10);
            Coordinator href = CoordinatorHelper.narrow(cm.getRef(coord));

            /* Register object to name service */
            cm.bindName("Coordinator",href);

            System.out.println("Coordinator ready and waiting ...") ;
            cm.runORB() ;

        } catch (Exception e) {
            System.err.println("ERROR: " + e) ;
            e.printStackTrace(System.out) ;
        }
      
        System.out.println("Coordinator Exiting ...") ;
    }
}









