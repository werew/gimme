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

    public boolean loginConsumer(Consumer c){ return false; }
    public boolean loginProducer(Producer p){ return false; }

    public static void main(String args[]) {

        if (args.length != 2) {
            System.out.println("Usage : java CoordinatorImpl" + " <machineServeurDeNoms>" + " <No Port>") ;
            return ;
        }

        try {
            // init ORB
            String [] argv = {"-ORBInitialHost", args[0], "-ORBInitialPort", args[1]} ; 
            ORB orb = ORB.init(argv, null) ;
            CoordinatorImpl helloImpl = new CoordinatorImpl() ;

            // init POA
            POA rootpoa =	POAHelper.narrow(orb.resolve_initial_references("RootPOA")) ;
            rootpoa.the_POAManager().activate() ;

            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(helloImpl) ;
            Coordinator href = CoordinatorHelper.narrow(ref) ;

            // inscription de l'objet au service de noms
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService") ;
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef) ;
            NameComponent path[] = ncRef.to_name( "Coordinator" ) ;
            ncRef.rebind(path, href) ;

            System.out.println("Coordinator ready and waiting ...") ;
            orb.run() ;

        } catch (Exception e) {
            System.err.println("ERROR: " + e) ;
            e.printStackTrace(System.out) ;
        }
      
        System.out.println("Coordinator Exiting ...") ;
    }
}









