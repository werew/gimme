# gimme

TODO
- Build a class for writing logs 
- Use human-readable ids (DONE)
- Production of resources (DONE)
- Get resources list from coordinator (DONE)
- Implement human cli (!)
- Strategies 
- Winning conditions (!!) (Maybe use setResources to get also the goal for each resource)
- Store transactions (TODO for production)
- Observe transactions (TODO handle stolen)
- Clean imports
- Protect concurrent access to resource in productor
- Turn management should be in commont to all agents

NOTES
- Explaining choice to use tie model to be able to use inheritance
  dont inherit from FooPOA:
  http://gokan-ekinci.developpez.com/apprendre-corba-java/
  http://docs.oracle.com/javase/7/docs/technotes/guides/idl/jidlTieServer.html
- Corba object comparaison: http://www.omniorb-support.com/pipermail/omniorb-list/2010-February/030522.html
- ORB is implicitly threaded http://docs.oracle.com/javase/7/docs/technotes/guides/idl/jidlFAQ.html#threading
- Using timestamps: loss of precision --> not a big problem as operations are commutatives
                    ==> result will not change
