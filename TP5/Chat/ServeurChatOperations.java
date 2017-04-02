package Chat;


/**
* Chat/ServeurChatOperations.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from Chat.idl
* dimanche 2 avril 2017 10 h 27 CEST
*/

public interface ServeurChatOperations 
{
  void login (String name, Chat.ClientChat chatter);
  void logout (String name);
  void broadcast (String name, String message);
  void chat (String myname, String hisname, String message);
  String whoIsLogged ();
  String ping ();
} // interface ServeurChatOperations
