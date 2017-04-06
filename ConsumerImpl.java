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
import Gimme.GameInfos;
import org.apache.commons.cli.*;

public class ConsumerImpl extends ConsumerPOA {

    boolean taketurns = false;
    private boolean human = false;
    Consumer mycons;
    Coordinator coordinator;
    ThreadRun orbthread;

    public void hello(){
        System.out.println("Hello!");
    }

    public int start(Producer[] p){
        p[0].queryAmount();
        return 0;
    }
    
    public void setHuman(){
        taketurns = true;
        human = true;
    }
    
    private static Options getOptions(){

        Option taketurns = new Option("t","taketurns",false, 
            "For games which are played in turns");
        Option human = new Option("h","human",false, 
            "Activate user interaction (for games played in turns)");

        Options options = new Options();
        options.addOption(taketurns); 
        options.addOption(human); 

        return options;
    }

    private static void printUsage(Options options, int exitcode){
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java ConsumerImpl [OPTIONS] <Name Server> <Port>", options);
        System.exit(exitcode);
    }

    public static void main(String args[]){

        Options options = getOptions();
        CommandLine cmd = null;
        String[] argz = null;

        try {
            /* Parse options */
            CommandLineParser parser = new DefaultParser();
            cmd = parser.parse(options, args);
            
            /* Check arguments */
            argz = cmd.getArgs();
            if (argz.length < 2) throw new ParseException("Argument missing");

        } catch (ParseException e) {

            System.out.println("\nERROR: "+e.getMessage()+"\n");
            printUsage(options,1);
        }


        /* Create and init consumer */
        ConsumerImpl consumer = new ConsumerImpl();

        if (cmd.hasOption('t')) consumer.taketurns = true;
        if (cmd.hasOption('h')) consumer.setHuman();


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
