#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.ga.ea.multiObjective.EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
