import org.omg.CORBA.ORB;
import org.omg.CORBA.UserException;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import org.omg.CosNaming.*;

public class CorbaManager {
    ORB orb;
    POA rootPOA;
    String refprelude;

    private void initORB(String host, String port){
            String [] argv = {"-ORBInitialHost", host,
                              "-ORBInitialPort", port } ; 
            orb = ORB.init(argv, null) ;
    }

    private void initRootPOA() throws UserException {
        rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA")) ;
        rootPOA.the_POAManager().activate() ;
    }

    public org.omg.CORBA.Object getRef(Servant obj) throws UserException {
            return rootPOA.servant_to_reference(obj) ;
    }

    public org.omg.CORBA.Object getRef(String name) throws UserException {
            return orb.string_to_object(refprelude + name) ;
    }

    public void bindName(String name, org.omg.CORBA.Object obj) throws UserException {
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService") ;
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef) ;
            NameComponent path[] = ncRef.to_name(name) ;
            ncRef.rebind(path, obj) ;
    }


    public void runORB(){
        try {
            orb.run();
        } catch (Exception e){
        }
    }

    public void stop(){
        try {
        //    orb.shutdown(true);
        } catch (Exception e){
        }
    }   

    public CorbaManager(String host, String port) throws UserException {
        initORB(host,port);
        initRootPOA();
        refprelude = "corbaname::" + host + ":" + port + "#";
    }

}









