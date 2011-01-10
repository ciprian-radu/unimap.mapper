#!/bin/bash

# Map a specified application using the following mapping algorithms:
# Simulated Annealing (with different variants), Branch and Bound

echo "usage:   ./map-app-zamolxe.sh {path to XML} --ctg {CTG ID} --apcg {APCG ID} {false|true} [seed]"
echo "example: ./map-app-zamolxe.sh ../CTG-XML/xml/VOPD --ctg 0 --apcg m false 1234567"

if [ $# -eq 6 -o $# -eq 7 ]
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
	echo "Nothing to do (incorrect usage; specify 6 or 7 parameters for this script; see usage)"	
fi

echo "Done"

