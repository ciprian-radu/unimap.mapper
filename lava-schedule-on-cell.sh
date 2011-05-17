#!/bin/bash

echo "This script uses the LAVA job scheduler to run a program multiple times, on computecell-000 and computecell-001."
echo "Note that the script passes to the job all the user specified program arguments,"
echo "plus "-s {n}", where n is the job's counter (it is intended as a random number generator seed)."
echo ""
echo "usage: ./lava-schedule-program-n-times.sh program n [program arguments]"
echo "(where program is the file path to the program and n is the number of times the program will run)"

args=("$@")
JOB_ARGS="${args[0]}"
for i in $(seq 2 ${#args[@]})
do
	JOB_ARGS="$JOB_ARGS ${args[$i]}"
done

for i in $(seq 1 $2)
do
	echo $i $2
	# we are running only on (Intel XEON) compute nodes 0 and 1
	# the last parameter (i) is the seed
	bsub -o job-%J.out.log -e job-%J.err.log -J $1-$i -m "computecell-000 computecell-001" $JOB_ARGS -s $i	
done

echo "Job $1 has been submitted $2 times."

