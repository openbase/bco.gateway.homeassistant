#!/bin/bash
set -e

NC='\033[0m'
RED='\033[0;31m'
GREEN='\033[0;32m'
ORANGE='\033[0;33m'
BLUE='\033[0;34m'
WHITE='\033[0;37m'

APP_NAME_RAW='bco-manager-homeassistant'
APP_NAME=${BLUE}${APP_NAME_RAW}${NC}
IMAGE_TAG=${1:-local}

echo -e "=== ${APP_NAME} build docker image...${WHITE}${NC}"

docker build -f docker/Dockerfile -t "openbaseorg/${APP_NAME_RAW}:${IMAGE_TAG}" .

# use this for debugging purpose: DOCKER_BUILDKIT=0 docker build -f docker/Dockerfile --progress=plain .
echo -e "=== ${APP_NAME} were ${GREEN}successfully${NC} build.${NC}"
