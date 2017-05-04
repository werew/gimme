import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.GameInfos;
import Gimme.Producer;
import Gimme.Registration;
import Gimme.ProducerPOATie;
import Gimme.Coordinator;
import Gimme.CoordinatorHelper;
import Gimme.ProducerOperations;
import Gimme.ProducerHelper;
import Gimme.Resource;
import org.apache.commons.cli.*;
import java.util.concurrent.locks.*;

public class ProducerImpl extends AgentImpl
implements ProducerOperations {
    // Reference to the IDL entity
    Producer myprod;

    // Coordinator of the game
    Coordinator coordinator; 
    
    // Resource produced 
    Timer timer;
    Resource resource;
    private Lock reslock; // to regulate concurrent access to the resource

    // This values should be modified only before
    // the game has started  
    private int total_produced = 0; // total amount of resource produced during the game
    int frequency_turns = 1; // production frequency for turn-based games (in turns)
    int frequency_ms = 500;  // production frequency for standard games (in milliseconds)
    float relative_prod = 0; // factor for relative production 
    int guaranteed_prod = 10;// amount of guaranteed production
    int capacity = -1;       // max stock capacity (-1 => infinity)
    int max_total = -1;      // max total production (-1 => infinity)

    /**
     * @brief ctor
     *  
     * Initialize the instance of the producer.
     * @param type type of the resource to produce
     * @param amount initial amount of the resource
     */    
    public ProducerImpl(String type, int amount){
        resource = new Resource(type,amount);
        reslock = new ReentrantLock();
    }
    
    /**
     * @brief wrapper for ProducerImpl(type,0)
     * @see ProducerImpl(String type, int amount)
     */
    public ProducerImpl(String type){
        this(type,0);
    }


    /* @brief start the game launching the production */
    public void start(){
            if (taketurns) {
                turnHandler();
            } else {
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    public void run() {
                       try { produce(); 
                       } catch (GameFinished gf){
                            this.cancel();
                            syncNotify();
                       }
                    }
                }, 0, frequency_ms);
            }
    }


    /* @brief Production logic for turn-based games */
    private void turnHandler() {
        int cnt = 0; 
        try {
            while (true) {
                if (cnt == frequency_turns){
                    produce();
                    cnt = 0;
                } else {
                    keepState();
                    cnt++;
                }
            }
        } catch (GameFinished gf){
            syncNotify();
        }
    }

    
    /* @brief do a production step */
    private void produce() throws GameFinished {
        turnActionPrologue();
        reslock.lock();

        // Actual potential production
        int p = (int) (resource.amount*relative_prod) + guaranteed_prod;
        
        // Respect max_total bound
        if (max_total != -1) p = Math.min(max_total-total_produced,p);
        
        // Respect capacity bound
        if (capacity != -1) p = Math.min(resource.amount-capacity,p);

        // Update resource
        resource.amount += p;
        total_produced += p;

        reslock.unlock();

        // Did we finish  ?
        if (max_total <= total_produced) {
            gamefinished.set(true);   // Prevent agent from playing
            coordinator.addTermProd(gameID); // Signal termination
        }


        turnActionEpilogue();

        Resource r = new Resource();
        r.type = resource.type; r.amount = p;
        addTransaction(Common.PRODUCTION,gameID,r);

        // If we finished, quit the game loop
        if (max_total <= total_produced) {
            throw new GameFinished();
        }
    }


    /**
     * @brief returns and consume the resource asked
     * @see Agent.getResource
     */    
    public Resource getResource(Resource request){
        reslock.lock();

        // Return 0 for invalid requests
        if (!request.type.equals(resource.type) || 
             request.amount > resource.amount   ){
            request.amount = 0;
            reslock.unlock();
            return request;
        }

        resource.amount -= request.amount;
        reslock.unlock();
        return request;
    }


    /**
     * @brief returns the current status of the resource
     *
     * Returns the status of the resource produced. This function
     * can be used by the Consumers to know the type and amount
     * of the resource produced by a Producer before trying to get it
     * @return a copy of the resource
     */
    public Resource queryResource(){
        Resource r = new Resource();
        reslock.lock();
        r.type = resource.type;
        r.amount = resource.amount;
        reslock.unlock();
        return r; 
    }


    /**
     * @brief join a game as a producer 
     *
     * Login into a coordinator with a given id order
     * to participate to the game
     * @param c reference to the Coordinator
     * @param id identifier to use for the Producer or "auto-set"
     * @return true if the join was successful, false otherwise
     */
    public boolean joinCoordinator(Coordinator c, String id){

        GameInfos gi = c.getGameInfos();

        if (gi.taketurns != this.taketurns){
            Log.error("Game style doesn't correspond: remove or add the -t option");
            return false;
        }

        Registration r = c.loginProducer(myprod,id,resource.type);
        if (r.logged == false){
            Log.info(r.msg);
            return false;
        }

        coordinator = c;
        gameID = r.id;
        return true;
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

        Option id = new Option("i","id",true, "Name to use for the game");
        Option taketurns = new Option("t","taketurns",false, 
            "Use this option to join games played in turns");
        Option start = new Option("s","start",true, 
            "Initial amount of product available (default is 0)");
        Option freq = new Option("f","frequency",true, 
            "Frequency of the production steps in milliseconds, "+
            "or in turns if -t has been set");
        Option relprod = new Option("r","relative-production",true, 
            "Produce a relative amount (rounded up) at each production step "+
            "(e.g. -r 1.5 will add 50% of the resource at each production step)");
        Option garprod  = new Option("g","guaranteed-production",true, 
            "Add a fixed amount at the resource stock at each production step");
        Option capacity = new Option("c","capacity",false, 
            "Max amount of the resource in stock");
        Option maxtotalprod = new Option("m","limit",true, 
            "Max total amount of resources produced (default is infinity)");

        Options options = new Options();
        options.addOption(id); 
        options.addOption(taketurns); 
        options.addOption(start); 
        options.addOption(freq); 
        options.addOption(relprod); 
        options.addOption(garprod); 
        options.addOption(maxtotalprod); 
        options.addOption(capacity); 

        return options;
    }


    /**
     * @brief Print usage and exit the program
     * @param options options to use for the usage description 
     * @param exitcode exit code of the program
     */
    private static void printUsage(Options options, int exitcode){
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java ProducerImpl [OPTIONS] <Name Server> <Port> <Type Resource>", options);
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
            if (argz.length < 3) throw new ParseException("Argument missing");

            /* Open a server */
            CorbaManager cm = new CorbaManager(argz[0],argz[1]);

            /* Create producer and tie to POA */
            ProducerImpl p = new ProducerImpl(argz[2], 
                cmd.hasOption('s') ? Integer.parseInt(cmd.getOptionValue('s')) : 0);
            ProducerPOATie tie = new ProducerPOATie(p, cm.rootPOA);
            p.myprod = tie._this(cm.orb);

            /* Get options and init producer */ 
            if (cmd.hasOption('m')) p.max_total = Integer.parseInt(cmd.getOptionValue('m'));
            if (cmd.hasOption('r')) p.relative_prod = Float.parseFloat(cmd.getOptionValue('r'));
            if (cmd.hasOption('g')) p.guaranteed_prod = Integer.parseInt(cmd.getOptionValue('g'));
            if (cmd.hasOption('c')) p.capacity = Integer.parseInt(cmd.getOptionValue('c'));
            if (cmd.hasOption('t')) p.setTurnGame();
            if (cmd.hasOption('f')) {
                if (p.taketurns) p.frequency_turns = Integer.parseInt(cmd.getOptionValue('f'));
                else  p.frequency_ms = Integer.parseInt(cmd.getOptionValue('f'));
            }

            /* Get coordinator */
            Coordinator coord = CoordinatorHelper.narrow(cm.getRef("Coordinator"));

            /* Login */
            String id = cmd.hasOption('i') ? cmd.getOptionValue('i') : "auto-set";
            if (p.joinCoordinator(coord,id) == false) return;

            /* Run server */
            p.thread = new ThreadRun(cm);
            p.thread.start();
            p.thread.waitJoinable();
            p.thread.shutdown();

        } catch (ParseException e) {
            Log.error("\nERROR: "+e.getMessage()+"\n");
            printUsage(options,1);

        } catch (Exception e) {
            Log.error("ERROR : " + e) ;
            e.printStackTrace(System.out) ;
        } 
    }
}
