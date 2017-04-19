import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.Producer;
import Gimme.Registration;
import Gimme.ProducerPOATie;
import Gimme.Coordinator;
import Gimme.CoordinatorHelper;
import Gimme.ProducerOperations;
import Gimme.ProducerHelper;
import Gimme.Resource;
import org.apache.commons.cli.*;

public class ProducerImpl extends AgentImpl
implements ProducerOperations {
    Producer myprod;
    Coordinator coordinator;
    Resource resource;

    boolean taketurns = false;
    int frequency = 1;
    float relative_prod = 0;
    int guaranteed_prod = 0;
    int total_produced = 0;
    int capacity = -1;
    int max_total = -1;

    private void produce(){
        // Actual potential production
        int p = (int) (resource.amount*relative_prod) + guaranteed_prod;
        
        // Respect max_total bound
        if (max_total != -1) p = Math.min(max_total-total_produced,p);
        
        // Respect capacity bound
        if (capacity != -1) p = Math.min(resource.amount-capacity,p);

        // Update resource
        resource.amount += p;
        total_produced += p;
    }

    public ProducerImpl(String type){
        this(type,0);
    }

    public ProducerImpl(String type, int amount){
        resource = new Resource(type,amount);
    }


    public Resource getResource(Resource request){
        if (request.type != resource.type || 
            request.amount > resource.amount){
            request.amount = 0;
            return request;
        }

        resource.amount -= request.amount;
        return request;
    }


    public Resource queryResource(){ return resource; }

    public boolean joinCoordinator(Coordinator c, String id){

        Registration r = c.loginProducer(myprod,id,resource.type);
        if (r.logged == false){
            logmsg(r.msg,2);
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

            /* Create corba object */
            ProducerImpl p = new ProducerImpl(argz[2]) ;
            ProducerPOATie tie = new ProducerPOATie(p, cm.rootPOA);
            p.myprod = tie._this(cm.orb);

            /* Get options */ 
            if (cmd.hasOption('f')) p.frequency = Integer.parseInt(cmd.getOptionValue('f'));
            if (cmd.hasOption('m')) p.max_total = Integer.parseInt(cmd.getOptionValue('m'));
            if (cmd.hasOption('r')) p.relative_prod = Float.parseFloat(cmd.getOptionValue('r'));
            if (cmd.hasOption('g')) p.guaranteed_prod = Integer.parseInt(cmd.getOptionValue('g'));
            if (cmd.hasOption('c')) p.capacity = Integer.parseInt(cmd.getOptionValue('c'));
            p.taketurns = cmd.hasOption('t');

            /* Get coordinator */
            Coordinator coord = CoordinatorHelper.narrow(cm.getRef("Coordinator"));

            /* Login */
            String id = cmd.hasOption('i') ? cmd.getOptionValue('i') : "auto-set";
            p.joinCoordinator(coord,id);

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
