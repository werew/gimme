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
import org.apache.commons.cli.*;

public class CoordinatorImpl extends CoordinatorPOA {

    private Vector<Consumer> consumers;
    private Vector<Producer> producers;

    public boolean loginConsumer(Consumer c){ 
        System.out.println("Login consumer");
        consumers.add(c);
        Producer[] p = new Producer[producers.size()];
        c.start(producers.toArray(p));
        return false; 
    }

    public boolean loginProducer(Producer p){ 
        System.out.println("Login producer");
        producers.add(p);
        return false; 
    }

    public CoordinatorImpl(int maxprod, int maxcons){
        consumers = new Vector<Consumer>(maxcons);
        producers = new Vector<Producer>(maxprod);
    }




    private static Options getOptions(){

        Options options = new Options();

        Option consumers = new Option("c","consumers",true, 
            "number of consumers of the game (default is 2)");
        consumers.setArgName("nb consumers");
        Option producers = new Option("p","producers",true, 
            "number of producers of the game (default is 2)");
        producers.setArgName("nb producers");

        options.addOption(consumers); 
        options.addOption(producers); 

        return options;


    }


    private static void printUsage(Options options){
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java CoordinatorImpl [OPTIONS] <Name Server> <Port>", options);
    }




    public static void main(String args[]) {

        String[] argz = null;
        Options options = getOptions();

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            argz = cmd.getArgs();
            if (argz.length < 2) throw new ParseException("Argument missing");


        } catch (ParseException e) {
            System.out.println("\nERROR: "+e.getMessage()+"\n");
            printUsage(options);
            System.exit(1);
        }


        try {


            /* Init corba service */
            CorbaManager cm = new CorbaManager(argz[0], argz[1]);
           
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









