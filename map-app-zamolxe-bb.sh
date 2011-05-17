#!/bin/bash

MAPPER="ro.ulbsibiu.acaps.mapper.bb.BranchAndBoundMapper"
export MAPPER=$MAPPER
exec ./map-app-zamolxe.sh $@
