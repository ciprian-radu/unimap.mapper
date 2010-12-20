#!/bin/bash

echo "This script uses the LAVA job scheduler to run a program multiple times."
echo ""
echo "usage: ./lava-schedule-program-n-times.sh program n"
echo "(where program is the file path to the program and n is the number of times the program will run)"
for i in `seq 1 $2`; do
	exec bsub -o job-%J.out.log -e job-%J.err.log -J $1-$2 $1
done
