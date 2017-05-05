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

# Thos are the default values
HOST=localhost
PORT=8912

#TODO
pkill -u coniglio tnameserv
pkill -u coniglio java

main ()
{
    ARGS=$(getopt -u -n $0 -o f:t -l help -- "$@")

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
            --help) print_help
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

    # Launch nameserver
    tnameserv -ORBInitialPort "$PORT" &
    sleep 1

    # Launch coordinator
    echo java CoordinatorImpl $COORD_OPTS "$HOST" "$PORT" "$GOAL" &
    java CoordinatorImpl $COORD_OPTS "$HOST" "$PORT" "$GOAL" &
    sleep 1

    # Launch players
    launch "java ConsumerImpl  ${HOST} ${PORT}" "$FILE_OPTC"
    launch "java ProducerImpl  ${HOST} ${PORT}" "$FILE_OPTP"
    
}
    

launch (){
    while IFS='' read -r line || [ -n "$line" ]; do
        echo $1 $line
        $1 $line &
    done < "$2"
}
    

print_help (){
    cat << END_HELP
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


