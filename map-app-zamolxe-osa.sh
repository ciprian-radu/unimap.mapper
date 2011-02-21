#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.osa.OptimizedSimulatedAnnealingMapper"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
