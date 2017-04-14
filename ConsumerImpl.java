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
import Gimme.Registration;
import Gimme.GameInfos;
import Gimme.Agent;
import java.util.concurrent.locks.*;
import org.apache.commons.cli.*;

public class ConsumerImpl extends AgentImpl 
implements ConsumerOperations {

    private Consumer mycons = null;

    /* My game infos */
    private HashMap<String,Integer> resources;
    private HashMap<String,ArrayList<Producer>> view;

    /* Other game actors */
    private Coordinator coordinator = null;
    Producer[] prods;
    Consumer[] cons;


    /* To manage turns */
    private boolean taketurns = false;
    private boolean human = false;
    private boolean isMyTurn;
    private Lock turnLock;
    private Condition turnAvailable;
    private Condition turnFinished;

    private void turnActionPrologue(){
        try {
            turnLock.lock();
            while (isMyTurn == false) turnAvailable.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void turnActionEpilogue(){
        try {
            isMyTurn = false; 
            turnFinished.signal(); 
        } catch (Exception e) {
            e.printStackTrace();
        } finally { // Ensure unlock
            turnLock.unlock();
        }
    }


    public void start(){
        teststrategy();
    }

    private void teststrategy(){
        while (true) {
            for (int i = 0; i < prods.length; i++){
                Resource r = queryResource_wr(prods[i]);
                logmsg(r.type+" "+r.amount,0);
                try{Thread.sleep(1000);
                } catch (Exception e) {}
            }
        }
    } 

    /* Wrappers */

    private Resource getResource_wr(Agent a, Resource request){
        turnActionPrologue();
        Resource r = a.getResource(request);
        turnActionEpilogue();
        return r;
    }

    private Resource queryResource_wr(Producer p){
        turnActionPrologue();
        Resource r = p.queryResource();
        turnActionEpilogue();
        return r;
    }

    public boolean playTurn(){
        try {
            turnLock.lock();
            while (isMyTurn == true) turnFinished.await();
            isMyTurn = true;
            turnAvailable.signal();
        } catch (Exception e){
            e.printStackTrace();
        } finally { // Ensure unlock
            turnLock.unlock();
        }

        return true;
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
    public boolean joinCoordinator(Coordinator c, String id){

        GameInfos gi = c.getGameInfos();

        /* Set game style */
        if (gi.taketurns == true){
            if (this.taketurns == false) setTurnGame();
	    } else if (this.human == true) {
            return false;
        }

        /* Cannot join running games */
        if (gi.running) return false;
       
        /* Login */ 
        logmsg(id,0);
        Registration r = c.loginConsumer(mycons,id);
        if (r.logged == false){
            logmsg(r.msg,2);
            return false;
        }
        
        logmsg("Login as "+r.id+": "+r.msg,1);
        coordinator = c;
        gameID = r.id;
        return true;
    }



    public void setTurnGame(){
        taketurns = true;
        isMyTurn = false;
        turnLock = new ReentrantLock();
        turnAvailable = turnLock.newCondition();
        turnFinished = turnLock.newCondition();
    }

    /* @brief Turn on human interaction */ 
    public void setHuman(){
        setTurnGame();
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
        Option id = new Option("i","id",true, 
            "Name to use for the game");

        Options options = new Options();
        options.addOption(human); 
        options.addOption(id); 

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
          
            String id = cmd.hasOption('i') ? cmd.getOptionValue('i') : "auto-set";
            if (consumer.joinCoordinator(coord,id) == false) {
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
