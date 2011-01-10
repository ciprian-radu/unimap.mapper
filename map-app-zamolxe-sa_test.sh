#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.sa.test.SimulatedAnnealingTestMapper"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
