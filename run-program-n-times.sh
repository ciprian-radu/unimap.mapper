#!/bin/bash

echo "usage: ./run-program-n-times.sh program n"
echo "(where program is the file path to the program and n is the number of times the program will run)"
for i in `seq 1 $2`; do
	exec $1
done