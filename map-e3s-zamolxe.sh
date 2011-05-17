#!/bin/bash

# Map all E3S benchmarks using the following mapping algorithms:
# Simulated Annealing (with different variants), Branch and Bound
#
# Note that only APCGs with ID 2 (MinExecTimeScheduler) are used

if [ -z $MAPPER ]
then
	MAPPER="ro.ulbsibiu.acaps.mapper.sa.SimulatedAnnealingMapper ro.ulbsibiu.acaps.mapper.bb.BranchAndBoundMapper ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestMapper ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestAttractionMoveMapper"
fi

ROUTING="false true"

CLASSPATH="../CTG-XML/classes:../NoC-XML/classes:./classes:$(echo `ls ./lib/*.jar` . | sed 's/ /:/g')"

APPLICATION="auto-indust-mocsyn.tgff consumer-mocsyn.tgff networking-mocsyn.tgff office-automation-mocsyn.tgff telecom-mocsyn.tgff auto-indust-mocsyn-asic.tgff consumer-mocsyn-asic.tgff networking-mocsyn-asic.tgff office-automation-mocsyn-asic.tgff telecom-mocsyn-asic.tgff"

for mapper in $MAPPER
do
	for routing in $ROUTING
	do
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 1 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 2 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 3 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3 --apcg 2 -r $routing
		
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/consumer-mocsyn.tgff --ctg 0 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/consumer-mocsyn.tgff --ctg 1 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/consumer-mocsyn.tgff --ctg 0+1 --apcg 2 -r $routing
		
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn.tgff --ctg 0 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn.tgff --ctg 1 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn.tgff --ctg 2 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn.tgff --ctg 3 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn.tgff --ctg 0+1+2+3 --apcg 2 -r $routing
		
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/office-automation-mocsyn.tgff --ctg 0 --apcg 2 -r $routing
		
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 0 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 1 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 2 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 3 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 4 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 5 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 6 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 7 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 8 --apcg 2 -r $routing
		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn.tgff --ctg 0+1+2+3+4+5+6+7+8 --apcg 2 -r $routing
		
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn-asic.tgff --ctg 0 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn-asic.tgff --ctg 1 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn-asic.tgff --ctg 2 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn-asic.tgff --ctg 3 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/auto-indust-mocsyn-asic.tgff --ctg 0+1+2+3 --apcg 2 -r $routing
		
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/consumer-mocsyn-asic.tgff --ctg 0 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/consumer-mocsyn-asic.tgff --ctg 1 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/consumer-mocsyn-asic.tgff --ctg 0+1 --apcg 2 -r $routing
		
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn-asic.tgff --ctg 0 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn-asic.tgff --ctg 1 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn-asic.tgff --ctg 2 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn-asic.tgff --ctg 3 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/networking-mocsyn-asic.tgff --ctg 0+1+2+3 --apcg 2 -r $routing
		
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/office-automation-mocsyn-asic.tgff --ctg 0 --apcg 2 -r $routing
		
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 0 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 1 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 2 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 3 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 4 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 5 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 6 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 7 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 8 --apcg 2 -r $routing
#		java -classpath $CLASSPATH $mapper ../CTG-XML/xml/e3s/telecom-mocsyn-asic.tgff --ctg 0+1+2+3+4+5+6+7+8 --apcg 2 -r $routing
	done
done

echo "Done"

