import static java.lang.System.out;



public class Log {

    // Commands
    static final String CLEAR_SCREEN = "2J";
    static final String CLEAR_LINE = "K";
  
    // Text attributes  
    static final String OFF = "0";
    static final String BOLD = "1";
    static final String UNDERSCORE = "2";
    static final String BLINK  = "3";
     
    // Foreground colors 
    static final String FBLACK = "30";
    static final String FRED = "31";
    static final String FGREEN = "32";
    static final String FYELLOW  = "33";
    static final String FBLUE = "34";
    static final String FMAGENTA = "35";
    static final String FCYAN = "36";
    static final String FWHITE = "37";

    // Background colors 
    static final String BBLACK = "40";
    static final String BRED = "41";
    static final String BGREEN = "42";
    static final String BYELLOW  = "43";
    static final String BBLUE = "44";
    static final String BMAGENTA = "45";
    static final String BCYAN = "46";
    static final String BWHITE = "47";


    static public String esc(String cmd){
        return "\033["+cmd+"m";
    }

    static public void theme(String cmd){
       out.println(esc(cmd)+esc(CLEAR_SCREEN));
    }

    static public void info(String msg){
       out.println(esc(FCYAN)+msg+esc(OFF)); 
    }

    static public void warning(String msg){
       out.println(esc(FYELLOW)+msg+esc(OFF)); 
    }

    static public void error(String msg){
       out.println(esc(FRED)+msg+esc(OFF)); 
    }

    static public void success(String msg){
       out.println(esc(FGREEN)+msg+esc(OFF)); 
    }
    
    static public String with(String msg, String cmd){
       return "\033["+cmd+"m"+msg+"\033[0m";       
    }

    static public void reset(){
       out.print("\033[0m");
    }
}
    


        

    
    
    

    
