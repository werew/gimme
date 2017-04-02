

build:
	idlj -fall Gimme.idl
	javac -classpath "Gimme:." *.java

clean:
	/bin/rm -rf Gimme orb.db
	/bin/rm -f *.class *~ \#* .\#*




