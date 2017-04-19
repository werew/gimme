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
import Gimme.Transaction;
import Gimme.CoordinatorHelper;
import Gimme.AgentPOA;
import Gimme.AgentHelper;
import Gimme.GameInfos;
import org.apache.commons.cli.*;
import java.util.concurrent.locks.*;

public abstract class AgentImpl extends AgentPOA {

    private Date date;

    protected String gameID;
    protected HashMap<String,Transaction> transactions;

    /* To manage turns */
    protected boolean taketurns = false;
    private boolean isMyTurn;
    private Lock turnLock;
    private Condition turnAvailable;
    private Condition turnFinished;

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
     * @brief prologue of a turn
     *
     * If taketurns == true this function should be called 
     * immediatly before the execution of any transaction
     */
    protected void turnActionPrologue(){
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
    protected void turnActionEpilogue(){
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

    protected void keepState(){
        turnActionPrologue();
        turnActionEpilogue();
    }


    /* TODO use log class to implement msg types */
    public void logmsg(String msg, int type){
        System.out.println(msg);
    }

    public abstract Resource getResource(Resource request);
    public abstract void start();

    public AgentImpl(){
        transactions = new HashMap<String,Transaction>();
        date = new Date();
    }

    protected Transaction addTransaction(int type, String from, Resource content){
        Transaction t = new Transaction();
        t.type     = type;
        t.from     = from;
        t.content  = content;

        synchronized (this){
            t.timestamp = date.getTime();
            t.id = gameID+"-"+transactions.size();
            transactions.put(t.id,t);
        }
        return t;
    }



}
