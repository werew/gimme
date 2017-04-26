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
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import java.lang.Thread;

public abstract class AgentImpl extends AgentPOA {

    private Date date; // To timestamp the transactions
    public ThreadRun thread; // Orb's thread. If not null this will be signaled
                             // as joinable when the game has finished

    /* Agent's game identifier */
    protected String gameID;

    /* Transactions carried out */
    protected ConcurrentHashMap<String,Transaction> transactions;

    /* Turn management */
    protected boolean taketurns = false;
    private boolean isMyTurn;
    private Lock turnLock;
    private Condition turnAvailable;
    private Condition turnFinished;

    /* Game finished ? */
    protected AtomicBoolean gamefinished;
    protected AtomicBoolean syncend;


    /* @brief ctor */
    public AgentImpl(){
        transactions = new ConcurrentHashMap<String,Transaction>();
        date = new Date();
        gamefinished = new AtomicBoolean(false); 
        syncend = new AtomicBoolean(false); 
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
        logmsg("~~~ start action",0); 
        if (gamefinished.get() == true) {
         logmsg("~~~ throw finished",0); 
         throw new GameFinished();
        }

        if (taketurns == false) return;

        try {
            turnLock.lock();
            logmsg("0) WAITING TURN",0); 
            while (isMyTurn == false) turnAvailable.await();

            // Check if game has finished while waiting
            if (gamefinished.get() == true) {
                turnLock.unlock();
                throw new GameFinished();
            }

            logmsg("1) INSIDE TURN",0); 
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
        logmsg("~~~ end action",0); 
        if (taketurns == false) return;
        try {
            isMyTurn = false; 
            turnFinished.signal(); 
            logmsg("2) ENDING TURN",0); 
        } catch (Exception e) {
            e.printStackTrace();
        } finally { // Ensure unlock
            turnLock.unlock();
            logmsg("3) OUTSIDE TURN",0); 
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
        logmsg("                <- PLAY",0);
        // Return immediatly if agent has finished the game
        if (gamefinished.get()) return true;
        logmsg("            OK  <- PLAY",0);

        try {
            turnLock.lock();

            // Offer a new turn
            isMyTurn = true;
            turnAvailable.signal();

            // Wait until turn is finished
            while (isMyTurn == true) turnFinished.await();
        } catch (Exception e){
            e.printStackTrace();
        } finally { // Ensure unlock
            turnLock.unlock();
        }
        logmsg("           DONE -> PLAY",0);
        return true;
    }


    protected void syncNotify(){
        synchronized (syncend) {
            syncend.set(true);
            try {
                syncend.notify();  
            } catch (Exception e){
                e.printStackTrace();
            }
        } 
    }

    public Transaction[] getHistory(){
        Transaction[] hst = new Transaction[transactions.size()];
        transactions.values().toArray(hst);
        return hst;
    }

    public void syncEnd(){
        // Stop current agent from playing
        try {
            synchronized (syncend){
                gamefinished.set(true); 
                while (syncend.get() == false)
                    syncend.wait();
            }
            logmsg("Sync end",0);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void endGame(String result){
        // Stop current agent from playing
        gamefinished.set(true); 

        // Unlock blocked prologues 
        if (taketurns) {
            turnLock.lock();
            isMyTurn = true;
            turnAvailable.signal();
            turnLock.unlock();
        }

        
        logmsg(result,0);
        logmsg("--------------------",0);
        if (thread != null) thread.setJoinable();
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
