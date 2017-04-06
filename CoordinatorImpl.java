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
import Gimme.GameInfos;
import org.apache.commons.cli.*;

public class CoordinatorImpl extends CoordinatorPOA {

    private Consumer[] consumers;
    private Producer[] producers;
    boolean taketurns = false;
    boolean running = false;

    public GameInfos getGameInfos(){
        GameInfos gi = new GameInfos();
        gi.taketurns = taketurns;
        gi.running = running;
        return gi;
    }

    public boolean loginConsumer(Consumer c){ 
        System.out.println("Login consumer");
        consumers[consumers.length] = c;
        c.start(producers);
        return false; 
    }

    public void resetProducers(int np){
        producers = new Producer[np];
    }

    public void resetConsumers(int nc){
        consumers = new Consumer[nc];
    }

    public boolean loginProducer(Producer p){ 
        System.out.println("Login producer");
        producers[producers.length] = p;
        return false; 
    }

    public CoordinatorImpl(int maxprod, int maxcons){
        resetConsumers(maxcons); resetProducers(maxprod);
    }




    private static Options getOptions(){

        Options options = new Options();

        Option taketurns = new Option("t","taketurns",false, 
            "if set the game will be played in turns");
        Option consumers = new Option("c","consumers",true, 
            "number of consumers of the game (default is 2)");
        consumers.setArgName("nb consumers");
        Option producers = new Option("p","producers",true, 
            "number of producers of the game (default is 2)");
        producers.setArgName("nb producers");

        options.addOption(taketurns); 
        options.addOption(consumers); 
        options.addOption(producers); 

        return options;
    }


    private static void printUsage(Options options, int exitcode){
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java CoordinatorImpl [OPTIONS] <Name Server> <Port>", options);
        System.exit(exitcode);
    }

    public static void main(String args[]) {

        CoordinatorImpl coord = new CoordinatorImpl(2,2);
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


        /* Init coodinator */
        if (cmd.hasOption('t')) coord.taketurns = true;
        if (cmd.hasOption('c')) coord.resetConsumers(
            Integer.parseInt(cmd.getOptionValue('c')));
        if (cmd.hasOption('p')) coord.resetProducers(
            Integer.parseInt(cmd.getOptionValue('p')));

        
        try {
            /* Init corba service */
            CorbaManager cm = new CorbaManager(argz[0], argz[1]);
           
            /* Create corba object */
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









