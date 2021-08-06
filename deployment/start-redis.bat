@rem ##########################################################################################
@rem # Starts Redis docker container with a default Redis 6379 port exposed to the localhost. #
@rem ##########################################################################################

docker run --rm --name "message-delivery-server-redis" -p 6379:6379 -d "redis:6-alpine"
