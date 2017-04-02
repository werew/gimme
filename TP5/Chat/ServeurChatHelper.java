package Chat;


/**
* Chat/ServeurChatHelper.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from Chat.idl
* dimanche 2 avril 2017 10 h 27 CEST
*/

abstract public class ServeurChatHelper
{
  private static String  _id = "IDL:Chat/ServeurChat:1.0";

  public static void insert (org.omg.CORBA.Any a, Chat.ServeurChat that)
  {
    org.omg.CORBA.portable.OutputStream out = a.create_output_stream ();
    a.type (type ());
    write (out, that);
    a.read_value (out.create_input_stream (), type ());
  }

  public static Chat.ServeurChat extract (org.omg.CORBA.Any a)
  {
    return read (a.create_input_stream ());
  }

  private static org.omg.CORBA.TypeCode __typeCode = null;
  synchronized public static org.omg.CORBA.TypeCode type ()
  {
    if (__typeCode == null)
    {
      __typeCode = org.omg.CORBA.ORB.init ().create_interface_tc (Chat.ServeurChatHelper.id (), "ServeurChat");
    }
    return __typeCode;
  }

  public static String id ()
  {
    return _id;
  }

  public static Chat.ServeurChat read (org.omg.CORBA.portable.InputStream istream)
  {
    return narrow (istream.read_Object (_ServeurChatStub.class));
  }

  public static void write (org.omg.CORBA.portable.OutputStream ostream, Chat.ServeurChat value)
  {
    ostream.write_Object ((org.omg.CORBA.Object) value);
  }

  public static Chat.ServeurChat narrow (org.omg.CORBA.Object obj)
  {
    if (obj == null)
      return null;
    else if (obj instanceof Chat.ServeurChat)
      return (Chat.ServeurChat)obj;
    else if (!obj._is_a (id ()))
      throw new org.omg.CORBA.BAD_PARAM ();
    else
    {
      org.omg.CORBA.portable.Delegate delegate = ((org.omg.CORBA.portable.ObjectImpl)obj)._get_delegate ();
      Chat._ServeurChatStub stub = new Chat._ServeurChatStub ();
      stub._set_delegate(delegate);
      return stub;
    }
  }

  public static Chat.ServeurChat unchecked_narrow (org.omg.CORBA.Object obj)
  {
    if (obj == null)
      return null;
    else if (obj instanceof Chat.ServeurChat)
      return (Chat.ServeurChat)obj;
    else
    {
      org.omg.CORBA.portable.Delegate delegate = ((org.omg.CORBA.portable.ObjectImpl)obj)._get_delegate ();
      Chat._ServeurChatStub stub = new Chat._ServeurChatStub ();
      stub._set_delegate(delegate);
      return stub;
    }
  }

}
