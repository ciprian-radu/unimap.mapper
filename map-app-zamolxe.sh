#!/bin/bash

# Map a specified application using the following mapping algorithms:
# Simulated Annealing (with different variants), Branch and Bound

echo "usage:   ./map-app-zamolxe.sh -b {path to XML} --ctg {CTG ID} --apcg {APCG ID} [-r {true|false}] -s {seed}"
echo "example: ./map-app-zamolxe.sh -b ../CTG-XML/xml/VOPD --ctg 0 --apcg m -s 1234567"

if [ -z $MAPPER ]
then
	MAPPER="ro.ulbsibiu.acaps.mapper.sa.SimulatedAnnealingMapper ro.ulbsibiu.acaps.mapper.bb.BranchAndBoundMapper ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestMapper ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestAttractionMoveMapper"
fi
	
CLASSPATH="../CTG-XML/classes:../NoC-XML/classes:./classes:$(echo `ls ./lib/*.jar` . | sed 's/ /:/g')"
	
for mapper in $MAPPER
do
	java -classpath $CLASSPATH $mapper $@
done

echo "Done"

