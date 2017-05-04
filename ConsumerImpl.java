import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
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
import static java.lang.System.out;

public class ConsumerImpl extends AgentImpl 
implements ConsumerOperations {

    private Consumer mycons = null;
    public boolean verbose = false;

    /* My game infos */
    private ConcurrentHashMap<String,Integer> resources;
    private AtomicBoolean protect_mode;
    private AtomicBoolean observation_mode;
    int ripsoff = 0;
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
        protect_mode = new AtomicBoolean(false);
        observation_mode = new AtomicBoolean(false);
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
        Registration r = c.loginConsumer(mycons,id);
        if (r.logged == false){
            Log.error(r.msg);
            return false;
        }
        
        out.println("Login as "+Log.with(r.id,Log.BOLD)+
                           ": "+Log.with(r.msg,Log.FGREEN));
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


    private void waitandsteal_strategy() throws GameFinished {
        float nb_prod = prods.size();
        float nb_cons = cons.size();
        float nb_res  = resources.size();

        if (nb_cons > 0){
            // Smart observation (calculate time based on game actors and params)
            float avrg_obs  = ((nb_prod+nb_res)/nb_cons)*(goal/20);
            startObservation();
            if (taketurns == false){
                try{Thread.sleep((int) avrg_obs*20);
                } catch (Exception e) {}
            } else {
                int wt = (int) avrg_obs;
                for (int i = 0; i < wt; i++) 
                    keepState();
            }
            stopObservation();
        }

        // Try to steal from the opponents 
        Set<String> targets =  cons.keySet();
        for (String restype : resources.keySet()){
            // Init max ripoff
            Resource ripoff = new Resource();
            ripoff.type = restype;
            ripoff.amount = goal/3;

            for (Iterator<String> i = targets.iterator(); i.hasNext();) {
                while (true) {
                    Resource loot = getResource_wr(i.next(),ripoff);
                    
                    // Success, go to next target
                    if (loot.amount > 0) break;
                    // Not enough, steal smaller amount
                    if (loot.amount == 0) 
                        ripoff.amount -= ripoff.amount/4;
                    // Was protecting, remove from targets
                    if (loot.amount < 0){
                        i.remove();
                        break;
                    }
                }
            }
        }

        // Get missing resources
        crumbeater_strategy(10);
    } 

    private void crumbeater_strategy(int crumb) throws GameFinished {
        // Get missing resources
        while (true) {

            String min_res = minRes_helper();
            // TODO what if not prods in view 
            for (String prod : view.get(min_res)){
                Resource req = new Resource();
                req.type = min_res; 
                req.amount = crumb;

                Resource r = getResource_wr(prod,req);
                if (r.amount == 0 && crumb > 1) crumb -= 1;
                else crumb += 1;
            }
        }
    }

    private <T> T randFromSet(Set<T> s){
        int nb = (int) Math.random() % s.size();
        int i = 0; 
        for (T e : s){
            if (i == nb) return e;
            i++;
        }
        return null;
    }

    private Resource randEatMin(int amount) throws GameFinished {

        String min_res = minRes_helper();
        Set<String> prods_ids = view.get(min_res);

        String target = randFromSet(prods_ids);
        if (target == null) target = randFromSet(prods.keySet());

        Resource req = new Resource();
        req.type = min_res; 
        req.amount = amount;

        return getResource_wr(target,req);
    }

    
    private void watchfuleye_strategy() throws GameFinished {
        int a = 1;
        while (true) {
            // 1) Observe
            startObservation();
            if (taketurns == false){
                try{Thread.sleep(500);
                } catch (Exception e) {}
            } else {
                keepState();
                keepState();
            }
            stopObservation();
            
            // 2) Protect if necessary
            if (steal_rate() > 0.1) {
                enterProtectedMode(); 
                if (taketurns == false){
                    try{Thread.sleep(500);
                    } catch (Exception e) {}
                } else {
                    keepState();
                    keepState();
                }
                leaveProtectedMode();
            }
            
            // 3) Get some resources
            Resource res = randEatMin(a);
            if (res.amount > 0) a += a/2 + 1;
            else a = 1;
        }    
    }

    private float steal_rate(){
        if (transactions.size() == 0) return 0;
        return (float) ripsoff / (float) transactions.size();
    }

    private String minRes_helper(){
        // Init min to the max value
        Resource min = new Resource();
        min.type = null;
        min.amount = goal;
       
        // Search the min resource 
        for (Map.Entry<String,Integer> e : resources.entrySet()){
            if (e.getValue() < min.amount){
                min.type = e.getKey();
                min.amount = e.getValue();
            }
        }
        return min.type;
    }
    
    /* XXX test function */
    private void teststrategy0() throws GameFinished {
        while (true) {
            startObservation();
            if (taketurns == false){
                try{Thread.sleep(2000);
                } catch (Exception e) {}
            } else {
                for (int i = 0; i < 3; i++) keepState();
            }
            stopObservation();
            Resource req = new Resource();
            req.type = "Dinero"; req.amount = 20;
            Resource r = getResource_wr("Producer-0",req);
        }
    } 


    /* XXX test function */
    private void teststrategy1() throws GameFinished {
        while (true) {
            for (String id : prods.keySet()){
                Resource req = new Resource();
                req.type = "Dinero"; req.amount = 10;
                Resource r = getResource_wr(id,req);
//                keepState();
                try{Thread.sleep(1000);
                } catch (Exception e) {}
            }
/*
            Resource req = new Resource();
            req.type = "Dinero"; req.amount = 10;
            String[] c = new String[cons.size()];
            cons.keySet().toArray(c);
*/
        //    Resource r = getResource_wr(c[0],req);
        //   logmsg("stolen :"+r.type+" "+r.amount,0);
            
        }
    } 


    /**
     * @brief start to observe transactions
     *
     * Add the Consumer to the observers list of
     * the other consumers
     */
    private void startObservation() throws GameFinished {
        if (verbose) Log.info("Starting observation...");
        turnActionPrologue();
        for (Consumer c : cons.values()){
            c.addObserver(gameID);
        }
        observation_mode.set(true);
        turnActionEpilogue();
    }


    /**
     * @brief stop to observe transactions
     *
     * Remove the Consumer from the observers list of
     * the other consumers
     */
    private void stopObservation() throws GameFinished {
        if (verbose) Log.warning("Stop observation");
        turnActionPrologue();
        for (Consumer c : cons.values()){
            c.removeObserver(gameID);
        }
        observation_mode.set(false);
        turnActionEpilogue();
    }

    private void enterProtectedMode() throws GameFinished {
        if (verbose) Log.info("Entering protected mode...");
        turnActionPrologue();
        protect_mode.set(true);
        turnActionEpilogue();
    }

    private void leaveProtectedMode() throws GameFinished {
        if (verbose) Log.warning("Leaving protection mode");
        turnActionPrologue();
        protect_mode.set(false);
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

        // Get the agent 
        Agent a = null;
        if (prods.containsKey(id)) a = prods.get(id);
        else if (cons.containsKey(id)) a = cons.get(id);
        else return null;

        if (verbose) Log.info("Getting "+request.amount+" of "+request.type+" from "+id);

        turnActionPrologue();
    
        // Perform request
        Resource r = a.getResource(request);

        // Update transactions
        Transaction t = addTransaction(Common.REQUEST,id,r); 
        
        // Diffuse transaction to observers
        for (String c : observers) 
            cons.get(c).seeTransaction(gameID,t);
    
        // Update resource
        updateResource(r.type,r.amount);

        // Did we finish the game ?
        if (goalReached()) {
            gamefinished.set(true);        // Prevent this agent from playing
            coordinator.addWinner(gameID); // Signal winner
            if (verbose) Log.success("--> GOAL REACHED <--");
        }

        turnActionEpilogue();

        // If we finished, quit the game loop
        if (gamefinished.get() == true){
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
    private Resource queryResource_wr(String id) throws GameFinished {
        if (prods.containsKey(id) == false) return null;
        Producer p = prods.get(id);

        if (verbose) Log.info("Querying "+id);

        turnActionPrologue();
        Resource r = p.queryResource();
        addToView(r.type,id); 
        Transaction t = addTransaction(Common.QUERY,id,r);
        for (String c : observers) 
            cons.get(c).seeTransaction(gameID,t);
        turnActionEpilogue();
        return r;
    }

    synchronized private void updateResource(String type, int add){
        Integer amount  = resources.get(type);
        if (amount == null) amount = new Integer(add);
        else amount += add;
        resources.put(type, amount);
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
        if (gamefinished.get()) {
            request.amount = 0;
            return request;
        }

        if (protect_mode.get()) {
            if (verbose) Log.warning("A thief was caught");
            request.amount = - (goal/10);
            return request;
        }

        if (resources.containsKey(request.type) == false || 
            resources.get(request.type) < request.amount){
            request.amount = 0;
            return request;
        }

        updateResource(request.type, -request.amount);

        return request;
    }


    /**
     * @brief Start game
     *
     * This function should be called by the Coordinator    
     * as a signal that the game has started
     */
    public void start(){
        try {
            if (human) {
                human_cli();    
            } else {
                switch (strategy) {
                    case 0: teststrategy0();
                        break;
                    case 1: teststrategy1();
                        break;
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        } catch (GameFinished gf){
            syncNotify();
            Log.info("Game has finished!");
        } 
    }


    private void show_commands(){
       out.println(
               "list of commands:\n"+
               " help                       print this help\n"+
               " show                       show global view\n"+
               " get <amount> <type> <from> get resource from an agent\n"+
               " query <producer>           query the state of a producer\n"+
               " pass [times]               do nothing\n"+
               " protect [times]            enter protection mode\n"+
               " observe [times]            enter observation mode"
        );
    }

    private void show_view(){
            // Show goal
            out.println(Log.with("-- GOAL: "+Log.with(goal+"",Log.FRED),Log.BOLD));

            // Show consumers 
            out.println(Log.with("-- Opponents: ",Log.BOLD));
            for (String con : cons.keySet()){
                out.println("    "+con);
            }

            // Show view of the production
            out.println(Log.with("-- Resources: ",Log.BOLD));
            HashSet<String> _printed_prods = new HashSet<String>();
            for (String res_type : resources.keySet()){
                int amount = resources.get(res_type);
                out.println("    "+res_type+" (stock: "+ Log.with(""+resources.get(res_type), 
                            amount >= goal ? Log.FGREEN : Log.FRED)+")");
                if (view.containsKey(res_type) == false) continue;
                for (String prod : view.get(res_type)){
                    out.println("        "+prod);
                    _printed_prods.add(prod);
                }
            } 
            if (_printed_prods.size() < prods.size()){
                out.println(Log.with("-- Unknown productions",Log.BOLD));
                for (String prod : prods.keySet()){
                    if (_printed_prods.contains(prod)) continue;
                    out.println("    "+prod);
                }
            }
            
    }

    private void invalid_cmd(){
        Log.error("Invalid command (type help)");
    }

    private void cleanState() throws GameFinished {
        if (protect_mode.get()) leaveProtectedMode();
        if (observation_mode.get()) stopObservation();
    }

    private void doNothing(int nturns) throws GameFinished {
        for (int i = 0; i < nturns; i++) keepState();
    }

    private void doNothing(String nturns) throws GameFinished {
         try {
            int times = Integer.parseInt(nturns);
            if (times < 0) throw new NumberFormatException();
            doNothing(times);
         } catch (NumberFormatException e) { 
            invalid_cmd();
         } 
    }
    
    private void cli_getResource(String amount, String type, String from) throws GameFinished {
         try {
            int a = Integer.parseInt(amount);
            if (a < 0) throw new NumberFormatException();

            Resource request = new Resource();
            request.amount = a; request.type = type;
            Resource res = getResource_wr(from,request);

            if (res == null) { 
                Log.error(from+" is not a valid agent");
            } else { 
                if (res.amount < 0)
                    Log.warning("You have been seen stealing: "+res.amount);
                else if (res.amount  == 0)
                    out.println("No luck...");
                else
                    Log.success("Got "+res.amount+" of "+res.type);
            }
         } catch (NumberFormatException e) { 
            invalid_cmd();
         } 
    }

    private void human_cli() throws GameFinished, IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String command = null;

        Log.info("Human cli open");
        out.print(Log.with("@_@> ",Log.FRED));

        while ((command = in.readLine()) != null){
            String[] args = command.split(" ");

            switch (args[0]){
                case "help": show_commands();
                    break;
                case "show": show_view();
                    break;
                case "pass": if (args.length > 1) doNothing(args[1]);
                             else doNothing(1);
                    break;
                case "get": cleanState();
                            if (args.length < 4) invalid_cmd();
                            else cli_getResource(args[1],args[2],args[3]); 
                    break;
                case "query": cleanState();
                              if (args.length < 1) invalid_cmd();
                              else {
                                  //TODO cli_queryResource
                                  Resource r = queryResource_wr(args[1]);
                                  if (r == null) Log.error(args[1]+" is not a producer");
                                  else Log.info(args[1]+" has "+r.amount+" units of "+r.type);
                              }
                    break;
                case "protect": if (protect_mode.get()) {
                                    Log.info("Protected mode is active");
                                    break;
                                }
                                cleanState(); 
                                enterProtectedMode();
                                if (args.length > 1) doNothing(args[1]);
                                else doNothing(1);
                    break;
                case "observe": if (observation_mode.get()){
                                    Log.info("Already observing");
                                    break;
                                }
                                cleanState();
                                startObservation();
                                if (args.length > 1) doNothing(args[1]);
                                else doNothing(1);
                    break;
                default: invalid_cmd();
            }
            out.print(Log.with("@_@> ",Log.FRED));
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
            this.resources.put(r,new Integer(0));
        }
    }

        

    /**
     * @brief Set the list of Consumers of the game
     * This method should be used by the Coordinator to inform
     * the Consumer about the other Consumers who are 
     * participating to the game
     * @param consumers Array of the consumers
     * @param ids Array containing the ids of the consumers
     */
    public void setConsumers(Consumer[] consumers, String[] ids){
        cons  = new HashMap<String,Consumer>();    
        for (int i = 0; i < consumers.length; i++){
            if (ids[i].equals(gameID)) continue;
            cons.put(ids[i],consumers[i]);
        }
    }


    /**
     * @brief Set the list of Producers of the game
     * This method should be used by the Coordinator to inform the
     * Consumer about which Producers are participating to the game
     * @param consumers Array of the consumers
     * @param ids Array containing the ids of the consumers
     */
    public void setProducers(Producer[] producers, String[] ids){
        prods = new HashMap<String,Producer>();    
        for (int i = 0; i < producers.length; i++){
            prods.put(ids[i],producers[i]);
        }
    }


    /**
     * @brief add a Consumer to the list of the observers
     * 
     * @param id identifier of the Consumer
     */
    public void addObserver(String id){
       observers.add(id); 
    }


    /**
     * @brief remove a Consumer from the list of the observers
     * 
     * @param id identifier of the Consumer
     */
    public void removeObserver(String id){
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
                if (prods.containsKey(t.from) && 
                    t.content.amount > 0){
                    // Update view
                    addToView(t.content.type,t.from); 
                }
                if (cons.containsKey(t.from) || 
                    t.from == gameID) ripsoff++;
                
                break;
            case Common.QUERY:
                addToView(t.content.type,t.from); 
                Log.info("Saw query: "+who+" got "+t.content.type+" from "+t.from);
                break;
        }
    }

    public Resource[] getResult(){
        Resource[] r = new Resource[resources.size()];
        int i = 0;
        for (Map.Entry<String,Integer> e : resources.entrySet()){
           Resource t = new Resource();
           t.type = e.getKey();
           t.amount = e.getValue();
           r[i++] = t;
        }
        return r;
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
        Option verbose = new Option("v","verbose",false, 
            "Print every action");

        Options options = new Options();
        options.addOption(human); 
        options.addOption(id); 
        options.addOption(strategy); 
        options.addOption(verbose); 

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
            if (cmd.hasOption('v')) consumer.verbose = true;

            /* Create a server */
            CorbaManager cm = new CorbaManager(argz[0],argz[1]);

            /* Create corba object */ 
            ConsumerPOATie tie = new ConsumerPOATie(consumer, cm.rootPOA);
            consumer.mycons = tie._this(cm.orb);

            /* Get coordinator */
            Coordinator coord = CoordinatorHelper.narrow(cm.getRef("Coordinator"));
          
            String id = cmd.hasOption('i') ? cmd.getOptionValue('i') : "auto-set";
            if (consumer.joinCoordinator(coord,id) == false) {
                Log.error("Impossible to join server") ;
                printUsage(options, 1);
            }

            /* Run server */
            consumer.thread = new ThreadRun(cm);
            consumer.thread.start();
            consumer.thread.waitJoinable();
            consumer.thread.shutdown();


        } catch (ParseException e) {
            Log.error("\nERROR: "+e.getMessage()+"\n");
            printUsage(options,1);

        } catch (Exception e) {
            Log.error("ERROR : " + e) ;
            e.printStackTrace(System.out) ;
        } 
    }
}

