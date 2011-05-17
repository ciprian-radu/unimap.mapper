#!/bin/bash

echo "This script uses the SUN Grid Engine job scheduler to run a program multiple times, at UPB."
echo "Note that the script passes to the job all the user specified program arguments,"
echo "plus "-s {n}", where n is the job's counter (it is intended as a random number generator seed)."
echo ""
echo "usage: ./upb-schedule.sh program n [program arguments]"
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
	# the last parameter (i) is the seed
	#qsub -o job-${JOB_ID}.out.log -e job-${JOB_ID}.err.log -N $1-$i -b y $JOB_ARGS -s $i
	qsub -q ibm-opteron.q,ibm-quad.q -b y $JOB_ARGS -s $i
done

echo "Job $1 has been submitted $2 times."

