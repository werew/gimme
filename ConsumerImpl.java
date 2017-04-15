import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.util.concurrent.*;
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
import Gimme.Transaction;
import java.util.concurrent.locks.*;
import org.apache.commons.cli.*;

public class ConsumerImpl extends AgentImpl 
implements ConsumerOperations {

    private Consumer mycons = null;

    /* My game infos */
    private HashMap<String,Integer> resources;
    private ConcurrentHashMap<String,ConcurrentSkipListSet<String>> view;
    private ConcurrentSkipListSet<String> observers;
    int strategy = 0;

    /* Other game actors */
    private Coordinator coordinator = null;
    private HashMap<String,Consumer> cons;
    private HashMap<String,Producer> prods;

    /* To manage turns */
    private boolean taketurns = false;
    private boolean human = false;
    private boolean isMyTurn;
    private Lock turnLock;
    private Condition turnAvailable;
    private Condition turnFinished;


    /**
     * @brief ctor
     */
    public ConsumerImpl(boolean human,int strategy){
        if (human) this.setHuman();
        resources = new HashMap<String,Integer>();	
        view = new ConcurrentHashMap<String,ConcurrentSkipListSet<String>>();
        observers = new ConcurrentSkipListSet<String>();
        this.strategy = strategy;
    }



    /**
     * @brief Try to join a coordinator
     *
     * Try to join a coordinator which is proposing a game
     * which parameters match the one of the consumer.
     * @param c Coordinator to join 
     * @param id Identifier desired or "auto-set"
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


    /**
     * @brief prepare for a game in turns
     *
     * Initialize the object so that it will 
     * play a game in turns
     */
    public void setTurnGame(){
        taketurns = true;
        isMyTurn = false;
        turnLock = new ReentrantLock();
        turnAvailable = turnLock.newCondition();
        turnFinished = turnLock.newCondition();
    }

    /**
     * @brief prepare for a game with human interaction 
     *
     * Initialize the object so that it will 
     * play a game in turns with human interaction
     */
    public void setHuman(){
        setTurnGame();
        human = true;
    }


    /**
     * @brief prologue of a turn
     *
     * If taketurns == true this function should be called 
     * immediatly before the execution of any transaction
     */
    private void turnActionPrologue(){
        if (taketurns == false) return;
        try {
            turnLock.lock();
            while (isMyTurn == false) turnAvailable.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * @brief epilogue of a turn
     *
     * If taketurns == true this function should be called 
     * immediatly after the execution of any transaction
     */
    private void turnActionEpilogue(){
        if (taketurns == false) return;
        try {
            isMyTurn = false; 
            turnFinished.signal(); 
        } catch (Exception e) {
            e.printStackTrace();
        } finally { // Ensure unlock
            turnLock.unlock();
        }
    }


    /* XXX test function */
    private void teststrategy0(){
        while (true) {
            startObservation();
            for (int i = 0; i < 3; i++) keepState();
            stopObservation();
        }
    } 

    private void teststrategy1(){
        while (true) {
            for (String id : prods.keySet()){
                Resource r = queryResource_wr(id);
                logmsg(r.type+" "+r.amount,0);
                try{Thread.sleep(1000);
                } catch (Exception e) {}
            }
        }
    } 


    private void keepState(){
        turnActionPrologue();
        turnActionEpilogue();
    }

    private void startObservation(){
        turnActionPrologue();
        for (Consumer c : cons.values()){
            c.addObserver(gameID);
        }
        turnActionEpilogue();
    }

    private void stopObservation(){
        turnActionPrologue();
        for (Consumer c : cons.values()){
            c.removeObserver(gameID);
        }
        turnActionEpilogue();
    }

    private void addToView(String product, String producer){
        ConcurrentSkipListSet<String> prod_set = null;
        if (view.containsKey(product) == false){
            prod_set = new ConcurrentSkipListSet<String>();
            view.put(product,prod_set);
        } else {
            prod_set = view.get(product);
        }
        prod_set.add(producer);
    }


    /**
     * Wrappers: this wrappers add some more actions to
     * the simple execution of a transaction (e.g. turn control,
     * transaction storage, etc...). When launching a transaction
     * always use a wrapper of the original IDL method's interface.
     */

    /* @brief getResource's wrapper */
    private Resource getResource_wr(String id, Resource request){
        turnActionPrologue();
        Agent a = null;
        if (prods.containsKey(id)) a = prods.get(id);
        else a = cons.get(id);
        Resource r = a.getResource(request);
        addTransaction(Common.REQUEST,id,r); 
        turnActionEpilogue();
        return r;
    }

    /* @brief queryResource's wrapper */
    private Resource queryResource_wr(String id){
        turnActionPrologue();
        Producer p = prods.get(id);
        Resource r = p.queryResource();
        addTransaction(Common.QUERY,id,r); 
        turnActionEpilogue();
        return r;
    }


    /*************** Consumer IDL's interface *****************
     * Interface: the following function implements 
     * methods of the IDL's interface Consumer 
     */


    public Resource getResource(Resource request){
        return new Resource("Test",0);
    }

    public void start(){
        switch (strategy) {
            case 0: teststrategy0();
                break;
            case 1: teststrategy1();
                break;
        }
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
        

    public void updateConsumers(Consumer[] consumers, String[] ids){
        cons  = new HashMap<String,Consumer>();    
        for (int i = 0; i < consumers.length; i++){
            if (ids[i].equals(gameID)) continue;
            cons.put(ids[i],consumers[i]);
        }
    }

    public void updateProducers(Producer[] producers, String[] ids){
        prods = new HashMap<String,Producer>();    
        for (int i = 0; i < producers.length; i++) 
            prods.put(ids[i],producers[i]);
    }

    public void addObserver(String id){
       observers.add(id); 
    }

    public void removeObserver(String id){
       observers.remove(id); 
    }


    public void seeTransaction(String who, Transaction t){
        switch (t.type) {
            case Common.REQUEST:
                if (cons.containsKey(t.from)){
                    // TODO Stolen
                    return;
                } else if (t.content.amount < 0){
                   addToView(t.content.type,t.from); 
                }
                break;
            case Common.QUERY:
               addToView(t.content.type,t.from); 
                break;
        }
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
        Option strategy = new Option("s","strategy",true, 
            "Which strategy should use the player");

        Options options = new Options();
        options.addOption(human); 
        options.addOption(id); 
        options.addOption(strategy); 

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
            int strategy = cmd.hasOption('s') ? 
                Integer.parseInt(cmd.getOptionValue('s')) : 0;
            ConsumerImpl consumer = new ConsumerImpl(cmd.hasOption('h'), strategy);

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
