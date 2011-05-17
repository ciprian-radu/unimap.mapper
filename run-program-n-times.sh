#!/bin/bash

echo "This script runs a program multiple times. The processes run in parallel (so make sure a process doesn't affect any other process)."
echo ""
echo "usage: ./run-program-n-times.sh program n"
echo "(where program is the file path to the program and n is the number of times the program will run)"
for i in `seq 1 $2`; do
	exec $1 &
done