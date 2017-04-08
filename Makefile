

build:
	idlj -fall Gimme.idl
	idlj -fallTIE Gimme.idl
	javac -classpath "Gimme:." *.java

clean:
	rm -f org/apache/commons/cli/*.class
	rm -rf Gimme orb.db
	rm -f *.class *~ \#* .\#*




