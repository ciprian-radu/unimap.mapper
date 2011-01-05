#!/bin/bash

# Map a specified application using the following mapping algorithms:
# Simulated Annealing (with different variants), Branch and Bound

echo "usage:   ./map-app-zamolxe.sh {path to XML} --ctg {CTG ID} --apcg {APCG ID}"
echo "example: ./map-app-zamolxe.sh ../CTG-XML/xml/VOPD --ctg 0 --apcg m"

if [ $# -eq 5 ]
then
	if [ -z $MAPPER ]
	then
		MAPPER="ro.ulbsibiu.acaps.mapper.sa.SimulatedAnnealingMapper ro.ulbsibiu.acaps.mapper.bb.BranchAndBoundMapper ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestMapper ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestAttractionMoveMapper"
	fi
	
	ROUTING="false true"
	
	CLASSPATH="../CTG-XML/classes:../NoC-XML/classes:./classes:$(echo `ls ./lib/*.jar` . | sed 's/ /:/g')"
	
	for mapper in $MAPPER
	do
		for routing in $ROUTING
		do
		java -classpath $CLASSPATH $mapper $@ $routing
		done
	done
else
	echo "Nothing to do (incorrect usage; specify 5 parameters for this script; see usage)"	
fi

echo "Done"

