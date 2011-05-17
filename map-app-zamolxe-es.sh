#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.es.ExhaustiveSearchMapper"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
