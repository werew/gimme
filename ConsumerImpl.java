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
import org.apache.commons.cli.*;

public class ConsumerImpl extends AgentImpl 
implements ConsumerOperations {

    private Consumer mycons = null;

    /* My game infos */
    private ConcurrentHashMap<String,Integer> resources;
    private ConcurrentHashMap<String,ConcurrentSkipListSet<String>> view;
    private ConcurrentSkipListSet<String> observers;
    private int strategy = 0;
    private boolean human = false;
    private int goal;

    /* Other game actors */
    private Coordinator coordinator = null;
    private HashMap<String,Consumer> cons;
    private HashMap<String,Producer> prods;




    /**
     * @brief ctor
     * 
     * @param human play game in human mode (only available 
     *        for turn-based games)
     * @param strategy id of the strategy to use for the game
     */
    public ConsumerImpl(boolean human,int strategy){
        if (human) this.setHuman();
        resources = new ConcurrentHashMap<String,Integer>();	
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
     *         successfully, false otherwise
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
     * @brief prepare for a game with human interaction 
     *
     * Initialize the object so that it will 
     * play a game in turns with human interaction
     */
    public void setHuman(){
        setTurnGame();
        human = true;
    }


    /* XXX test function */
    private void teststrategy0(){
        logmsg("teststrat0",0);
        while (true) {
            startObservation();
            for (int i = 0; i < 3; i++) keepState();
            stopObservation();
        }
    } 


    /* XXX test function */
    private void teststrategy1() throws GameFinished {
        logmsg("teststrat1",0);
        while (true) {
            for (String id : prods.keySet()){
                Resource req = new Resource();
                req.type = "Dinero"; req.amount = 2;
                Resource r = getResource_wr(id,req);
                logmsg(r.type+" "+r.amount,0);
                try{Thread.sleep(1000);
                } catch (Exception e) {}
            }
        }
    } 


    /**
     * @brief start to observe transactions
     *
     * Add the Consumer to the observers list of
     * the other consumers
     */
    private void startObservation(){
        turnActionPrologue();
        logmsg("startobs",0);
        for (Consumer c : cons.values()){
            c.addObserver(gameID);
        }
        turnActionEpilogue();
    }


    /**
     * @brief stop to observe transactions
     *
     * Remove the Consumer from the observers list of
     * the other consumers
     */
    private void stopObservation(){
        turnActionPrologue();
        logmsg("stopobs",0);
        for (Consumer c : cons.values()){
            c.removeObserver(gameID);
        }
        turnActionEpilogue();
    }


    /**
     * @brief update view
     *
     * Update the view adding the relation between a product
     * and a producer
     * @param product name of the resource produced
     * @param produced id of the producer which produce product
     */
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
    private Resource getResource_wr(String id, Resource request) throws GameFinished {
        turnActionPrologue();
        
        // Get the agent 
        Agent a = null;
        if (prods.containsKey(id)) a = prods.get(id);
        else a = cons.get(id);
    
        // Perform request
        Resource r = a.getResource(request);

        // Update transactions
        Transaction t = addTransaction(Common.REQUEST,id,r); 
        
        // Diffuse transaction to observers
        for (String c : observers) 
            cons.get(c).seeTransaction(gameID,t);
    
        // Update resource
        Integer amount = resources.get(r.type);
        if (amount == null) amount = new Integer(r.amount);
        else amount += r.amount;
        resources.put(r.type, amount);

        turnActionEpilogue();
        
        // Did we finish the game ?
        if (goalReached()) {
            coordinator.addWinner(gameID);
            throw new GameFinished();
        }

        return r;
    }

    private boolean goalReached(){
        for (Integer v : resources.values()){
           if (v < goal) return false;
        }
        return true;
    }

    /* @brief queryResource's wrapper */
    private Resource queryResource_wr(String id){
        turnActionPrologue();
        logmsg("queryres",0);
        Producer p = prods.get(id);
        Resource r = p.queryResource();
        Transaction t = addTransaction(Common.QUERY,id,r);
        for (String c : observers) 
            cons.get(c).seeTransaction(gameID,t);
        turnActionEpilogue();
        return r;
    }


    /*************** Consumer IDL's interface *****************
     * Interface: the following function implements 
     * methods of the IDL's interface Consumer 
     */

    /**
     * @brief consume a resource from this Consumer
     *
     * This method can be used to steal resources from the
     * Consumer in the same way as resources are consumed from
     * a Producer
     * @see ProducerImpl.getResource
     */
    public Resource getResource(Resource request){
        // TODO
        return new Resource("Test",0);
    }


    /**
     * @brief Start game
     *
     * This function should be called by the Coordinator    
     * as a signal that the game has started
     */
    public void start(){
        try {
            switch (strategy) {
                case 0: teststrategy0();
                    break;
                case 1: teststrategy1();
                    break;
            }
        } catch (GameFinished e){
            logmsg("Game has finished!",0);
        }
    }


    /**
     * @brief 
     * 
     */
    public void setGoal(int goal, String[] resources){
        this.goal = goal;
        view = new ConcurrentHashMap<String,ConcurrentSkipListSet<String>>();
        for (String r : resources){
            ConcurrentSkipListSet<String> prod_set = new ConcurrentSkipListSet<String>();
            view.put(r,prod_set);
        }
    }

        

    /**
     * @brief Set the list of Consumers of the game
     * TODO modify in setConsumers
     * This method should be used by the Coordinator to inform
     * the Consumer about the other Consumers who are 
     * participating to the game
     * @param consumers Array of the consumers
     * @param ids Array containing the ids of the consumers
     */
    public void updateConsumers(Consumer[] consumers, String[] ids){
        cons  = new HashMap<String,Consumer>();    
        for (int i = 0; i < consumers.length; i++){
            if (ids[i].equals(gameID)) continue;
            logmsg("Put consumer "+ids[i],0);
            cons.put(ids[i],consumers[i]);
        }
    }


    /**
     * @brief Set the list of Producers of the game
     * TODO modify in setProducers
     * This method should be used by the Coordinator to inform the
     * Consumer about which Producers are participating to the game
     * @param consumers Array of the consumers
     * @param ids Array containing the ids of the consumers
     */
    public void updateProducers(Producer[] producers, String[] ids){
        prods = new HashMap<String,Producer>();    
        for (int i = 0; i < producers.length; i++){
            logmsg("Put producer "+ids[i],0);
            prods.put(ids[i],producers[i]);
        }
    }


    /**
     * @brief add a Consumer to the list of the observers
     * 
     * @param id identifier of the Consumer
     */
    public void addObserver(String id){
       logmsg("Adding "+id,0);
       observers.add(id); 
    }


    /**
     * @brief remove a Consumer from the list of the observers
     * 
     * @param id identifier of the Consumer
     */
    public void removeObserver(String id){
       logmsg("Removing "+id,0);
       observers.remove(id); 
    }

    
    /**
     * @brief Handle an observed Transaction
     * 
     * This method is used to show a Transaction to the
     * Consumer when this last is observing.
     * @param who id of the observee
     * @param t transaction observed
     */
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
                logmsg("Saw query: "+who+" got "+t.content.type+" from "+t.from,0);
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

class GameFinished extends Exception {}
