import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.Producer;
import Gimme.Consumer;
import Gimme.Resource;
import Gimme.Coordinator;
import Gimme.CoordinatorHelper;
import Gimme.ConsumerOperations;
import Gimme.ConsumerPOATie;
import Gimme.ConsumerHelper;
import Gimme.GameInfos;
import org.apache.commons.cli.*;

public class ConsumerImpl extends AgentImpl 
implements ConsumerOperations {

    private Consumer mycons = null;

    boolean taketurns = false;
    private boolean human = false;
    private HashMap<String,Integer> resources;
    private HashMap<String,ArrayList<Producer>> view;

    private Coordinator coordinator = null;
    Producer[] prods;
    Consumer[] cons;

    ThreadRun orbthread;

    public void start(){
        teststrategy();
    }

    private void teststrategy(){
        for (int i = 0; i < prods.length; i++){
            Resource r = prods[i].queryResource();
            logmsg(r.type+" "+r.amount,0);
        }
    } 

    public ConsumerImpl(boolean human){
        if (human) this.setHuman();
        resources = new HashMap<String,Integer>();	
        view = new HashMap<String,ArrayList<Producer>>();
    }


    public void feed(Producer[] p, Consumer[] c){
        prods = p; cons = c;
    }

    public Resource getResource(Resource request){
        return new Resource("Test",0);
    }

    /**
     * @brief Try to join a coordinator
     *
     * Try to join a coordinator which is proposing a game
     * which parameters match the one of the consumer.
     * @param c Coordinator to join 
     * @return true if the coordinator has been joined 
     *         succesfully, false otherwise
     */
    public boolean joinCoordinator(Coordinator c){

        GameInfos gi = c.getGameInfos();

        /* Set game style */
        if (gi.taketurns != this.taketurns){
            if (this.human && !gi.taketurns) return false;
            this.taketurns = gi.taketurns;
	    }

        /* Cannot join running games */
        if (gi.running) return false;
       
        /* Login */ 
        if (c.loginConsumer(mycons) != Common.SUCCESS) return false;
        coordinator = c;
        return true;
    }



    /* @brief Turn on human interaction */ 
    public void setHuman(){
        taketurns = true;
        human = true;
    }

   

    /**
     * @brief Generate cli options
     * 
     * Retrieve an object Options containing all the
     * command line options used by the consumer
     * executable and their description
     *
     * @return an object of type Options
     */
    private static Options getOptions(){

        Option human = new Option("h","human",false, 
            "Activate user interaction (for games played in turns)");

        Options options = new Options();
        options.addOption(human); 

        return options;
    }



    /**
     * @brief Print usage and exit the program
     * @param options options to use for the usage description 
     * @param exitcode exit code of the program
     */
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

            /* Create and init consumer */
            ConsumerImpl consumer = new ConsumerImpl(cmd.hasOption('h'));

            /* Create a server */
            CorbaManager cm = new CorbaManager(argz[0],argz[1]);

            /* Create corba object */ 
            ConsumerPOATie tie = new ConsumerPOATie(consumer, cm.rootPOA);
            consumer.mycons = tie._this(cm.orb);

            /* Get coordinator */
            Coordinator coord = CoordinatorHelper.narrow(cm.getRef("Coordinator"));
            
            if (consumer.joinCoordinator(coord) == false) {
                System.out.println("Impossible to join server") ;
                printUsage(options, 1);
            }


            /* Run server */
            cm.runORB();

        } catch (ParseException e) {
            System.out.println("\nERROR: "+e.getMessage()+"\n");
            printUsage(options,1);

        } catch (Exception e) {
            System.out.println("ERROR : " + e) ;
            e.printStackTrace(System.out) ;
        } 
    }
}
