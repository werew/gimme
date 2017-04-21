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
import java.util.concurrent.atomic.*;

public abstract class AgentImpl extends AgentPOA {

    private Date date;

    /* Agent's game identifier */
    protected String gameID;

    /* Transactions carried out */
    protected HashMap<String,Transaction> transactions;

    /* Turn management */
    protected boolean taketurns = false;
    private boolean isMyTurn;
    private Lock turnLock;
    private Condition turnAvailable;
    private Condition turnFinished;

    /* Game finished ? */
    protected AtomicBoolean gamefinished;


    /* @brief ctor */
    public AgentImpl(){
        transactions = new HashMap<String,Transaction>();
        date = new Date();
        gamefinished = new AtomicBoolean(false); 
    }

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
     * This function should be called immediately before the execution of any
     * turn action (or transaction) 
     */
    protected void turnActionPrologue() throws GameFinished {
        if (gamefinished.get()) {
            turnFinished.signal(); // Unlock playTurn() if necessary
            throw new GameFinished();
        }

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
     * This function should be called immediately after the execution of any
     * turn action (or transaction)
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


    /**
     * @brief confers the permission to play a turn
     * 
     * This method is called by the Coordinator to let
     * an Agent play his turn
     * TODO return ??
     */
    public boolean playTurn(){
        if (gamefinished.get()) return true;
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


    public void setGameFinished(){
        gamefinished.set(true);
    }


    /* @brief empty turn (do nothing) */
    protected void keepState() throws GameFinished {
        turnActionPrologue();
        turnActionEpilogue();
    }


    /**
     * @brief returns and consume the resource asked
     *
     * Get a certain quantity of a resource from the Agent
     * @param request a instance of Resource where type
     *        represent the type of the resource requested
     *        and amount the quantity to consume
     * @return an instance of Resource which indicates the 
     *        resource type and the amount obtained. 
     *        In case of success the returned resource is
     *        the same as request. An amount of zero
     *        indicates that the requested resource or amount
     *        was not available
     */
    public abstract Resource getResource(Resource request);


    /* @brief launch game */ 
    public abstract void start();


    /**
     * @brief create and store a transaction
     *
     * Create and store a Transaction into the list
     * transactions.
     * @param type type of the transaction
     * @param from id of the other agent implied
     * @param content exchanged content
     * @return added Transaction
     */
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


    /* TODO use log class to implement msg types */
    public void logmsg(String msg, int type){
        System.out.println(msg);
    }

}
