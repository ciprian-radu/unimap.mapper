#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.ga.ea.EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
