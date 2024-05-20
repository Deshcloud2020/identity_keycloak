#! /bin/bash
set -euxo pipefail

UUID=${1:-$(git rev-parse --short HEAD)}

INSTALL_NAMESPACE=${2:-default}

TARGET_NAMESPACES=${3-$INSTALL_NAMESPACE}

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# This version translates to one day for ttl.sh
VERSION="86400000.0.0"

# Build Keycloak Docker image (the keycloak tar.gz should already be in the container folder)
(
  cd $SCRIPT_DIR/../../quarkus/container
  
  docker build --build-arg KEYCLOAK_DIST=$(ls keycloak-*.tar.gz) . -t "ttl.sh/${UUID}keycloak:${VERSION}"
  docker push "ttl.sh/${UUID}keycloak:${VERSION}"
)

# Build the operator Docker image
(
  cd $SCRIPT_DIR/../../
  mvn clean package -Poperator -pl :keycloak-operator -am \
    -Dquarkus.container-image.build=true \
    -Dquarkus.container-image.image="ttl.sh/${UUID}keycloak-operator:${VERSION}" \
    -Dkc.operator.keycloak.image="ttl.sh/${UUID}keycloak:${VERSION}" \
    -DskipTests
  # JIB patching on images doesn't work reliably with ttl.sh
  docker push "ttl.sh/${UUID}keycloak-operator:${VERSION}"
)

$SCRIPT_DIR/prepare-olm-test.sh ttl.sh ${VERSION} NONE ${UUID} $TARGET_NAMESPACES

$SCRIPT_DIR/install-keycloak-operator.sh $INSTALL_NAMESPACE
