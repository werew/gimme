import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import org.omg.CosNaming.*;
import Gimme.Consumer;
import Gimme.Registration;
import Gimme.Producer;
import Gimme.Resource;
import Gimme.Coordinator;
import Gimme.CoordinatorPOA;
import Gimme.CoordinatorHelper;
import Gimme.GameInfos;
import Gimme.Transaction;
import Gimme.Agent;
import org.apache.commons.cli.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

public class CoordinatorImpl extends CoordinatorPOA {

    /* Logged consumers and producers */
    HashMap<String,Producer> producers;
    HashMap<String,Consumer> consumers;

    /* Resource to Producers mapping */
    HashMap<String,ArrayList<Producer>> resources;

    /* CorbaManager containing the orb of the
       current game. If not null this will be
       stopped once the game has finished   */
    public CorbaManager cm;

    /* Some infos on the game */
    private int ncons = 0;
    private int nprod = 0;
    private boolean taketurns = false;
    private boolean running = false;
    private AtomicBoolean gamefinished;
    private ArrayList<Transaction> transactions;

    /* End game infos */
    private ArrayList<String> winners;
    private Lock lockwinners;
    private ArrayList<String> endprod;
    private Lock lockendprod;
    public String path_out = null;

    /* Some game values */
    int goal;
    final int coutdown = 1;
    final String fullmsg  = "Game is full";
    final String readymsg = "Game will start in 5 seconds!";
    final String startmsg = "Game has started!";
    final String usedid   = "Id is already in use";

    public CoordinatorImpl(int maxprod, int maxcons){
        ncons = maxcons; nprod = maxprod;
        consumers = new HashMap<String,Consumer>();
        producers = new HashMap<String,Producer>();
        resources = new HashMap<String,ArrayList<Producer>>();
        winners = new ArrayList<String>();
        lockwinners = new ReentrantLock();
        endprod = new ArrayList<String>();
        lockendprod = new ReentrantLock();
        gamefinished = new AtomicBoolean(false);
        transactions = new ArrayList<Transaction>();
    }


    public GameInfos getGameInfos(){
        GameInfos gi = new GameInfos();
        gi.taketurns = taketurns;
        gi.running = running;
        return gi;
    }

    public Registration loginConsumer(Consumer c, String id){ 
        System.out.println("Login consumer");

        /* Check if game is full */
        if (consumers.size() == ncons) {
            return new Registration(false,id,fullmsg);
        }

        /* Check id */
        if (id.equals("auto-set")){
            id = "Consumer-"+consumers.size();
        } else if (consumers.containsKey(id)){
            return new Registration(false,id,usedid);
        }

        /* Successfully login */
        consumers.put(id,c);
        if (testStartGame()) launchGame();

        return new Registration(true,id,"Success");
    }

    public Registration loginProducer(Producer p, String id, String resource){ 
        System.out.println("Login producer");

        /* Check if game is full */
        if (producers.size() == nprod) {
            return new Registration(false,id,fullmsg);
        }

        /* Check id */
        if (id.equals("auto-set")){
            id = "Producer-"+producers.size();
        } else if (producers.containsKey(id)){
            return new Registration(false,id,usedid);
        }

        /* Add resource */
        if (resources.containsKey(resource)){
            resources.get(resource).add(p);
        } else {
            ArrayList<Producer> ap = new ArrayList<Producer>();
            ap.add(p);
            resources.put(resource,ap);
        }

        /* Successfully login */
        producers.put(id,p);
        if (testStartGame()) launchGame();

        return new Registration(true,id,"Success");

    }

    /* @brief add consumer to winners */
    public void addWinner(String id){
        lockwinners.lock();
        if (winners.contains(id) == false)
            winners.add(id);
        lockwinners.unlock();

        if (winners.size() == consumers.size()) {
            synchronized (gamefinished){
                 gamefinished.set(true); 
                 gamefinished.notify();
            }
        }
    }

    /* @brief add producer to terminated */
    public void addTermProd(String id){
        lockendprod.lock();
        if (endprod.contains(id) == false)
            endprod.add(id);
        lockendprod.unlock();

        if (endprod.size() == producers.size()){
            synchronized (gamefinished){
                 gamefinished.set(true); 
                 gamefinished.notify();
            }
        }
    }

    private void buildGameModel(){
        // Get all transactions 
        for (Agent a : consumers.values()) {
            Transaction[] t = a.getHistory();
            transactions.addAll(Arrays.asList(t));
        }
        for (Agent a : producers.values()) {
            Transaction[] t = a.getHistory();
            transactions.addAll(Arrays.asList(t));
        }

        // Sort transactions
        Collections.sort(transactions, new Comparator<Transaction> () {
            public int compare(Transaction t1, Transaction t2){
                return (int) (t1.timestamp-t2.timestamp);
            }
        });

    }

    private void writeGameModel(){
        try {
            FileWriter f = new FileWriter(path_out);
            f.write(goal+"\n");
            for (String c : consumers.keySet()) f.write(c+" ");
            f.write("\n");
            for (String p : producers.keySet()) f.write(p+" ");
            f.write("\n");
            for (String r : resources.keySet()) f.write(r+" ");
            f.write("\n");
            for (Transaction t : transactions){
                f.write(t.type+" "+t.to+" "+t.from+" "+
                        t.content.type+" "+t.content.amount+"\n"
                       );
            }
            f.close();
        } catch (IOException e){
            System.out.println(e.toString());
        }
        

    }

    private void endGame(){

        System.out.println("End game");

        if (taketurns == false){
            for (Agent a : consumers.values()) {
                a.syncEnd();
            }
            for (Agent a : producers.values()) {
                a.syncEnd();
            }
        }

        // Build and write game model to file
        if (path_out != null){
            buildGameModel();
            writeGameModel();
        }

        // Build ranking 
        ArrayList<Map.Entry<String,Integer>> l  = new ArrayList<Map.Entry<String,Integer>>();
        for (Map.Entry<String,Consumer> c : consumers.entrySet()){
            Resource[] res  = c.getValue().getResult();
            int points = 0;
            for (Resource r : res)
                if (r.amount >= goal) points++;
            l.add(new AbstractMap.SimpleEntry(c.getKey(), new Integer(points)));
        }
        Collections.sort(l, new Comparator<Map.Entry<String,Integer>>() {
            public int compare(Map.Entry<String,Integer> e1, Map.Entry<String,Integer> e2){
                return Integer.compare(e1.getValue(),e2.getValue());
            }
        });

        String ranking = ""; 
        for (Map.Entry<String,Integer> e : l){
            ranking += e.getKey()+" (score: "+e.getValue()+")\n";
        }

        for (Agent a : consumers.values()) a.endGame(ranking);
        for (Agent a : producers.values()) a.endGame(ranking);

        

    }


    private boolean testStartGame(){
        return ( running == false && 
                 ncons   == consumers.size() &&
                 nprod   == producers.size()
               );
    }

    private void initGame(){
        /* Send start message */
        broadcastMsg(producers.values(),startmsg,0);
        broadcastMsg(consumers.values(),startmsg,0);

        /* Build array of producers */ 
        Producer[] list_producers = new Producer[producers.size()];
        producers.values().toArray(list_producers);

        /* Build array of producers ids */ 
        String[] prods_ids = new String[producers.size()];
        producers.keySet().toArray(prods_ids);

        /* Build array of consumers */
        Consumer[] list_consumers = new Consumer[consumers.size()];
        consumers.values().toArray(list_consumers);

        /* Build array of consumers ids */
        String[] cons_ids = new String[consumers.size()];
        consumers.keySet().toArray(cons_ids);

        /* Build array of resources */
        String[] list_resources = new String[resources.size()];
        resources.keySet().toArray(list_resources);

        /* Initialize consumers  */
        for (Consumer c : consumers.values()){
            c.setProducers(list_producers,prods_ids);
            c.setConsumers(list_consumers,cons_ids);
            c.setGoal(goal, list_resources);
        }
    }

    private void gameLoop(){
        for (Consumer c : consumers.values()) c.start();
        for (Producer p : producers.values()) p.start();

        if (taketurns == true) {
            List<Consumer> cons = new ArrayList(consumers.values());
            while (gamefinished.get() == false) {
                for (Consumer c : cons) c.playTurn();
                for (Producer p : producers.values()) p.playTurn();
                Collections.shuffle(cons);
            }
        } else {
            synchronized (gamefinished){
                while (gamefinished.get() == false) {
                    try { gamefinished.wait();
                    } catch (Exception e) {
                       e.printStackTrace();
                    }
                }
            }
        }
    }



    private void launchGame(){
        System.out.println("Launching Game");
        broadcastMsg(producers.values(),readymsg,0);
        broadcastMsg(consumers.values(),readymsg,0);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                initGame();
                gameLoop();                
                endGame();
                if (cm != null) cm.stop();
            }
        }, coutdown * 1000);
        
    }

    private void broadcastMsg(Collection<? extends Agent> agents, String msg, int type){
        for (Agent a : agents) a.logmsg(msg);
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
        Option file = new Option("f","file",true, 
            "write game model to file");
        file.setArgName("output game model");

        options.addOption(taketurns); 
        options.addOption(consumers); 
        options.addOption(producers); 
        options.addOption(file); 

        return options;
    }


    private static void printUsage(Options options, int exitcode){
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java CoordinatorImpl [OPTIONS] <Name Server> <Port> <Goal>", options);
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
            if (argz.length < 3) throw new ParseException("Argument missing");

            /* Init coodinator */
            if (cmd.hasOption('t')) coord.taketurns = true;
            if (cmd.hasOption('c')) 
                coord.ncons = Integer.parseInt(cmd.getOptionValue('c'));
            if (cmd.hasOption('f')) 
                coord.path_out = cmd.getOptionValue('f');
            if (cmd.hasOption('p'))
                coord.nprod = Integer.parseInt(cmd.getOptionValue('p'));
            coord.goal = Integer.parseInt(argz[2]);

            /* Init corba service */
            CorbaManager cm = new CorbaManager(argz[0], argz[1]);
           
            /* Create corba object */
            Coordinator href = CoordinatorHelper.narrow(cm.getRef(coord));

            /* Register object to name service */
            cm.bindName("Coordinator",href);

            System.out.println("Coordinator ready and waiting ...") ;
            coord.cm = cm;
            cm.runORB() ;

        } catch (ParseException e) {
            System.out.println("\nERROR: "+e.getMessage()+"\n");
            printUsage(options,1);

        } catch (Exception e) {
            System.err.println("ERROR: " + e) ;
            e.printStackTrace(System.out) ;
        }
      
        System.out.println("Coordinator Exiting ...") ;
    }
}









