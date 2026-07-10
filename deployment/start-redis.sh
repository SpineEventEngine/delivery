#!/usr/bin/env bash

##########################################################################################
# Starts Redis docker container with a default Redis 6379 port exposed to the localhost. #
##########################################################################################

CONTAINER_NAME="${CONTAINER_NAME:-delivery-server-redis}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:6-alpine}"

echo "Starting Redis docker image '${REDIS_IMAGE}' in a '${CONTAINER_NAME}' docker container."
docker run --rm --name "${CONTAINER_NAME}" -p 6379:6379 -d "${REDIS_IMAGE}"
