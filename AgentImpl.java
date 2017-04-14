import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.Producer;
import Gimme.Resource;
import Gimme.Agent;
import Gimme.Coordinator;
import Gimme.CoordinatorHelper;
import Gimme.AgentPOA;
import Gimme.AgentHelper;
import Gimme.GameInfos;
import org.apache.commons.cli.*;

public abstract class AgentImpl extends AgentPOA {

    protected String gameID;

    /* TODO use log class to implement msg types */
    public void logmsg(String msg, int type){
        System.out.println(msg);
    }

    public abstract Resource getResource(Resource request);

}
