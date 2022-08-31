#!/bin/bash
# waitForContainerSetup.sh

set -e

# Max query attempts before consider setup failed
MAX_TRIES=90

function keycloak() {
  docker-compose logs keycloak | grep "Admin console listening"
}

function redis() {
  docker-compose logs redis | grep "Ready to accept connections"
}

function vault() {
  docker-compose logs vault | grep "upgrading keys finished"
}

function waitUntilServiceIsReady() {
  attempt=1
  while [ $attempt -le $MAX_TRIES ]; do
    if "$@"; then
      echo "$2 container is up!"
      break
    fi
    echo "Waiting for $2 container... (attempt: $((attempt++)))"
    sleep 10
  done

  if [ $attempt -gt $MAX_TRIES ]; then
    echo "Error: $2 not responding, cancelling set up"
    exit 1
  fi
}

waitUntilServiceIsReady redis "Redis is ready"
waitUntilServiceIsReady vault "Vault is ready"
waitUntilServiceIsReady keycloak "Keycloack is ready"
