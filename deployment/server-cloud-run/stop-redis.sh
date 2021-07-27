#!/usr/bin/env bash

##########################################################################################
# Stops Redis docker container.                                                          #
##########################################################################################

CONTAINER_NAME="${CONTAINER_NAME:-message-delivery-server-redis}"

echo "Stopping Redis docker container '${CONTAINER_NAME}'."
docker container stop "${CONTAINER_NAME}"
