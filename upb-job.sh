#!/bin/bash

echo "This is the job script used by SUN Grid Engine job scheduler, at UPB."
echo "This script runs another (secondary) script, multiple times."
echo "Note that the script passes to the secondary script all the user specified program arguments,"
echo "plus "-s {n}", where n is a counter (it is intended as a random number generator seed)."
echo ""
echo "usage: ./upb-schedule.sh program n i [program arguments]"
echo "(where program is the file path to the program, n is the number of times the program will run and i is the starting value for the seed counter)"

cd ~/workspace/Mapper

args=("$@")
JOB_ARGS="${args[0]}"
for i in $(seq 3 ${#args[@]})
do
	JOB_ARGS="$JOB_ARGS ${args[$i]}"
done

let end=$3+$2-1
for i in $(seq $3 $end)
do
	echo "Executing $JOB_ARGS -s $i"
	# the last parameter (i) is the seed
	exec $JOB_ARGS -s $i &
	wait
done
