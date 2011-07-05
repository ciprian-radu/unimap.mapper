#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.ga.ea.EnergyAndTemperatureAwareJMetalEvolutionaryAlgorithmMapper"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
