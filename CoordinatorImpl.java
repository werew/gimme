import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import org.omg.CosNaming.*;
import Gimme.Consumer;
import Gimme.Registration;
import Gimme.Producer;
import Gimme.Coordinator;
import Gimme.CoordinatorPOA;
import Gimme.CoordinatorHelper;
import Gimme.GameInfos;
import Gimme.Agent;
import org.apache.commons.cli.*;

public class CoordinatorImpl extends CoordinatorPOA {

    /* Logged consumers and producers */
    HashMap<String,Producer> producers;
    HashMap<String,Consumer> consumers;
    HashMap<String,ArrayList<Producer>> resources;
    private int ncons = 0;
    private int nprod = 0;

    /* Some infos on the game */
    private boolean taketurns = false;
    private boolean running = false;

    /* Some game values */
    final int coutdown = 5;
    final String fullmsg  = "Game is full";
    final String readymsg = "Game will start in 5 seconds!";
    final String startmsg = "Game has started!";
    final String usedid   = "Id is already in use";

    public CoordinatorImpl(int maxprod, int maxcons){
        ncons = maxcons; nprod = maxprod;
        consumers = new HashMap<String,Consumer>();
        producers = new HashMap<String,Producer>();
        resources = new HashMap<String,ArrayList<Producer>>();
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


    private boolean testStartGame(){
        return ( running == false && 
                 ncons   == consumers.size() &&
                 nprod   == producers.size()
               );
    }


    private void launchGame(){
        System.out.println("Launching Game");
        broadcastMsg(producers.values(),readymsg,0);
        broadcastMsg(consumers.values(),readymsg,0);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                broadcastMsg(producers.values(),startmsg,0);
                broadcastMsg(consumers.values(),startmsg,0);

                /* Send list of producers and opponents to the consumers */
                Producer[] list_producers = new Producer[producers.size()];
                producers.values().toArray(list_producers);
                String[] prods_ids = new String[producers.size()];
                producers.keySet().toArray(prods_ids);

                Consumer[] list_opponents = new Consumer[consumers.size()];
                consumers.values().toArray(list_opponents);
                String[] cons_ids = new String[consumers.size()];
                consumers.keySet().toArray(cons_ids);

                for (Consumer c : consumers.values()){
                    c.updateProducers(list_producers,prods_ids);
                    c.updateConsumers(list_opponents,cons_ids);
                }
                
                for (Consumer c : consumers.values()) c.start();

                while (true) {
                    for (Consumer c : consumers.values()) c.playTurn();
                }
                    
                //for (Producer p : producers);// p.start();
            }
        }, coutdown * 1000);

        /* TODO: for every consumer */
        // c.start(producers, consumers);
        
    }

    private void broadcastMsg(Collection<? extends Agent> agents, String msg, int type){
        for (Agent a : agents) a.logmsg(msg,type);
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

            /* Init coodinator */
            if (cmd.hasOption('t')) coord.taketurns = true;
            if (cmd.hasOption('c')) 
                coord.ncons = Integer.parseInt(cmd.getOptionValue('c'));
            if (cmd.hasOption('p'))
                coord.nprod = Integer.parseInt(cmd.getOptionValue('p'));

            /* Init corba service */
            CorbaManager cm = new CorbaManager(argz[0], argz[1]);
           
            /* Create corba object */
            Coordinator href = CoordinatorHelper.narrow(cm.getRef(coord));

            /* Register object to name service */
            cm.bindName("Coordinator",href);

            System.out.println("Coordinator ready and waiting ...") ;
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









