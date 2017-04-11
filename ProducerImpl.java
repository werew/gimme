import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.Producer;
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

        Option maxamount = new Option("m","max",false, 
            "Max amount of the resource");

        Options options = new Options();
        options.addOption(maxamount); 

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
            ProducerImpl producer = new ProducerImpl(argz[2]) ;
            ProducerPOATie tie = new ProducerPOATie(producer, cm.rootPOA);
            producer.myprod = tie._this(cm.orb);

            /* Get coordinator */
            producer.coordinator = CoordinatorHelper.narrow(cm.getRef("Coordinator"));

            /* Login */
            producer.coordinator.loginProducer(producer.myprod);

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
