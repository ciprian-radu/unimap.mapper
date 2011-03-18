#!/bin/bash

# Map a specified application using the following mapping algorithms:
# Simulated Annealing (with different variants), Branch and Bound

echo "usage:   ./map-app-zamolxe.sh -b {path to XML} --ctg {CTG ID} --apcg {APCG ID} [-r {true|false}] -s {seed}"
echo "example: ./map-app-zamolxe.sh -b ../CTG-XML/xml/VOPD --ctg 0 --apcg m -s 1234567"

if [ $# -eq 8 -o $# -eq 10 ]
then
	if [ -z $MAPPER ]
	then
		MAPPER="ro.ulbsibiu.acaps.mapper.sa.SimulatedAnnealingMapper ro.ulbsibiu.acaps.mapper.bb.BranchAndBoundMapper ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestMapper ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestAttractionMoveMapper"
	fi
	
	CLASSPATH="../CTG-XML/classes:../NoC-XML/classes:./classes:$(echo `ls ./lib/*.jar` . | sed 's/ /:/g')"
	
	for mapper in $MAPPER
	do
		java -classpath $CLASSPATH $mapper $@
	done
else
	echo "Nothing to do (incorrect usage; specify 8 or 10 (if you use routing) parameters for this script; see usage)"	
fi

echo "Done"

