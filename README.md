# gimme

TODO
- Winning conditions (!!) (DONE: setGoal to get the goal)
                          (DONE: use exception to quit strategy)
                          (DONE: check flag at each action)
                          (DONE: broadcast end event)
                          (DONE: finish when all producers are done)
                          (DONE: make ranking)
                          (DONE: signal end producer)
                          (DONE: broadcast ranking)
                          (DONE: exit clients)
                          (DONE: exit coordinator)
                          (TODO: finish cond based on resource availability)

- Implement human cli (!)
- Strategies 
- Clean imports
- Build a class for writing logs 

- Protect concurrent access to resources Consumer (DONE)
- Protect concurrent access to resource in productor (DONE)
- Turn management should be in commont to all agents (DONE)
- Observe transactions (DONE)
- Use human-readable ids (DONE)
- Production of resources (DONE)
- Store transactions (DONE)
- Get resources list from coordinator (DONE)

NOTES
- Explaining choice to use tie model to be able to use inheritance
  dont inherit from FooPOA:
  http://gokan-ekinci.developpez.com/apprendre-corba-java/
  http://docs.oracle.com/javase/7/docs/technotes/guides/idl/jidlTieServer.html
- Corba object comparaison: http://www.omniorb-support.com/pipermail/omniorb-list/2010-February/030522.html
- ORB is implicitly threaded http://docs.oracle.com/javase/7/docs/technotes/guides/idl/jidlFAQ.html#threading
- Using timestamps: loss of precision --> not a big problem as operations are commutatives
                    ==> result will not change
- End conditions: all consumers reached goal, all producers are terminated (problem => non terminated
                  cannot offer all resources )
