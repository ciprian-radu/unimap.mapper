#!/bin/bash

echo "This script uses the LAVA job scheduler to run a program multiple times."
echo ""
echo "usage: ./lava-schedule-program-n-times.sh program n [maximum 7 program arguments]"
echo "(where program is the file path to the program and n is the number of times the program will run)"

for i in $(seq 1 $2)
do
	echo $i $2
	# we are running only on (Intel XEON) compute nodes 0 and 1
	# the last parameter (i) is the seed
	bsub -o job-%J.out.log -e job-%J.err.log -J $1-$i -m "computenode-000 computenode-001" $1 $3 $4 $5 $6 $7 $8 -s $i
done

echo "Job $1 has been submitted $2 times."

