#!/bin/sh

# Error values
ERR_INVOPT=1
ERR_PUSAGE=2
ERR_BADFILE=3


# Some global vars
FILE_OPTC=""
FILE_OPTP=""
OUT_FILE=""
GOAL=0
TURNS=false
END=""

# Thos are the default values
HOST=localhost
PORT=8912
PID=$$


main ()
{
    ARGS=$(getopt -u -n $0 -o e:hf:t -l help -- "$@")

    if [ $? -ne 0 ]
    then
        error $ERR_INVOPT # Test valid options
    fi

    set -- $ARGS
    
    for arg
    do
        case $arg in 
            -f) OUT_FILE="$2" ; shift 2
                ;;
            -t) TURNS=true; shift
                ;;
            
            -h | --help) print_help
                ;;
            -e) END="$2"; shift 2
                ;;
            --) shift; break
                ;;
        esac
    done

    # Check mandatory args
    if [ $# -lt 3 ]
    then
        error $ERR_PUSAGE
    elif ! [ -f $1 ] || ! [ -f $2 ]
    then 
        error $ERR_BADFILE
    fi

    FILE_OPTC="$1"
    FILE_OPTP="$2"
    GOAL="$3"

    
    # Get nb of producers and consumers    
    NB_C=$(cat $FILE_OPTC | wc -l)
    NB_P=$(cat $FILE_OPTP | wc -l)
    if [ $NB_C -le 0 ] || [ $NB_P -le 0 ] 
    then
        error $ERR_BADFILE
    fi

    # Build options coordinator
    COORD_OPTS="-c $NB_C -p $NB_P"
    if $TURNS ; then
        COORD_OPTS="$COORD_OPTS -t"
    fi
    if [ -n "$OUT_FILE" ] ; then
        COORD_OPTS="$COORD_OPTS -f $OUT_FILE"
    fi
    if [ -n "$END" ] ; then
        COORD_OPTS="$COORD_OPTS -e $END"
    fi

    # Launch nameserver
    tnameserv -ORBInitialPort "$PORT" &
    sleep 1

    # Launch coordinator
    echo java CoordinatorImpl $COORD_OPTS "$HOST" "$PORT" "$GOAL" &
    java CoordinatorImpl $COORD_OPTS "$HOST" "$PORT" "$GOAL" &
    PID_COORD=$!
    sleep 1

    # Launch players
    launch "java ConsumerImpl  ${HOST} ${PORT}" "$FILE_OPTC"
    launch "java ProducerImpl  ${HOST} ${PORT}" "$FILE_OPTP"

    wait $PID_COORD
}

    

launch (){
    while IFS='' read -r line || [ -n "$line" ]; do
        echo $1 $line
        $1 $line &
    done < "$2"
}
   
quit (){
    kill -- -$(ps -o pgid=$PID | grep -o '[0-9]*')
    exit 0
} 
trap quit 2
trap quit EXIT


print_help (){
    cat << END_HELP
'$0' can be used to execute multiple instances of ConsumerImpl
and ProducerImpl at the same time. As the configuration of
these programs is quite rich, this script takes directly as 
parameter two files containing (at each line) the command line 
options of the consumers and the producers you want to launch.
The number of consumers and producers depends on the number
of lines in the option files.

usage: $0 [options] <file optconsumers> <file optproducers> <goal>

-f <file>                 save game transactions into a file
-t                        set the game style to turn-based 
-e                        set end timer
-h --help                 show this help
                                                               
END_HELP
    exit 0
}

error(){
    case $1 in
        $ERR_PUSAGE) echo "usage: $0 [options] <file optconsumers> <file optproducers> <goal>";; 
        $ERR_INVOPT) echo "Try: '$0 --help' for more informations";;
        $ERR_BADFILE) echo "Bad file";;
    esac >&2
    exit 1
}

main "$@"


