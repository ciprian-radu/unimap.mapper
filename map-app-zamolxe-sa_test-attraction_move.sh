#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestAttractionMoveMapper"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
