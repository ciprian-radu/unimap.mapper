#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.osa.OptimizedSimulatedAnnealingWithoutClusteringMapper"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
