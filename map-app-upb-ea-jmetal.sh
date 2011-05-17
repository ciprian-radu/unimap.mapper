#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.ga.ea.EnergyAwareJMetalEvolutionaryAlgorithmMapper"
export MAPPER=$MAPPER
cd ~/workspace/Mapper
exec ./map-app-zamolxe.sh $@
