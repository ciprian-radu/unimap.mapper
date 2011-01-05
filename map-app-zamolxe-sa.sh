#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.sa.SimulatedAnnealingMapper"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
