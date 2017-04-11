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
import Gimme.Agent;
import org.apache.commons.cli.*;

public class CoordinatorImpl extends CoordinatorPOA {

    /* Logged consumers and producers */
    private Consumer[] consumers; 
    private Producer[] producers;
    private int ncons = 0;
    private int nprod = 0;

    /* Some infos on the game */
    private boolean taketurns = false;
    private boolean running = false;

    /* Some game values */
    final int coutdown = 5;
    final String readymsg = "Game will start in 5 seconds!";
    final String startmsg = "Game has started!";

    public CoordinatorImpl(int maxprod, int maxcons){
        resetConsumers(maxcons); resetProducers(maxprod);
    }

    public void resetProducers(int np){
        nprod = 0; 
        producers = new Producer[np];
    }

    public void resetConsumers(int nc){
        ncons = 0;
        consumers = new Consumer[nc];
    }

    public GameInfos getGameInfos(){
        GameInfos gi = new GameInfos();
        gi.taketurns = taketurns;
        gi.running = running;
        return gi;
    }

    public int loginConsumer(Consumer c){ 
        System.out.println("Login consumer");

        /* Check if game is full */
        if (ncons == consumers.length) return Common.FULL;
        consumers[ncons++] = c;

        if (testStartGame()) launchGame();

        return Common.SUCCESS; 
    }

    public int loginProducer(Producer p){ 
        System.out.println("Login producer");

        /* Check if game is full */
        if (nprod == producers.length) return Common.FULL;
        producers[nprod++] = p;

        if (testStartGame()) launchGame();

        return Common.SUCCESS; 
    }


    private boolean testStartGame(){
        return ( running == false && 
                 ncons   == consumers.length &&
                 nprod   == producers.length
               );
    }

    private void launchGame(){
        System.out.println("Launching Game");
        broadcastMsg(producers,readymsg,0);
        broadcastMsg(consumers,readymsg,0);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                broadcastMsg(producers,startmsg,0);
                broadcastMsg(consumers,startmsg,0);

                /* Send list of producers and opponents to the consumers */
                Consumer[] opponents = new Consumer[consumers.length-1];
                for (int i = 0; i < consumers.length; i++){
                    for (int j = 0; j < opponents.length; j++){
                        opponents[j] = consumers[ j < i ? j : j+1];
                    }
                    consumers[i].feed(producers,opponents);
                }
            }
        }, coutdown * 1000);

        /* TODO: for every consumer */
        // c.start(producers, consumers);
        
    }

    private void broadcastMsg(Agent[] agents, String msg, int type){
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









