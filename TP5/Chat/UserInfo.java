package Chat;


/**
* Chat/UserInfo.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from Chat.idl
* dimanche 2 avril 2017 10 h 27 CEST
*/

public final class UserInfo implements org.omg.CORBA.portable.IDLEntity
{
  public String name = null;
  public Chat.ClientChat chatter = null;

  public UserInfo ()
  {
  } // ctor

  public UserInfo (String _name, Chat.ClientChat _chatter)
  {
    name = _name;
    chatter = _chatter;
  } // ctor

} // class UserInfo
