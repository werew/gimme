import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import java.util.*;
import java.io.*;
import java.io.IOException;
import org.omg.CosNaming.*;
import Gimme.Producer;
import Gimme.Agent;
import Gimme.Coordinator;
import Gimme.CoordinatorHelper;
import Gimme.AgentPOA;
import Gimme.AgentHelper;
import Gimme.GameInfos;
import org.apache.commons.cli.*;

public class AgentImpl extends AgentPOA {
    /* TODO use log class to implement msg types */
    public void logmsg(String msg, int type){
        System.out.println(msg);
    }
}
