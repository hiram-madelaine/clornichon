#!/usr/bin/env bash
# Usage: CLOJARS_USERNAME=... CLOJARS_PASSWORD=<deploy token> ./release.sh major|minor|patch
set -e

: "${CLOJARS_USERNAME:?CLOJARS_USERNAME is not set}"
: "${CLOJARS_PASSWORD:?CLOJARS_PASSWORD is not set}"

# Bump the version, spit it into src/scenari/meta.clj, commit and tag (no push yet).
# metav prints its SemVer records before the tag: keep the last line only
tag=$(clj -M:release "$1" --spit --output-dir src --namespace scenari.meta --formats clj --without-push | tail -1)
echo "Tagged $tag"

./build.sh
./deploy.sh

# Push only once Clojars accepted the artifact
git push origin HEAD "$tag"
echo "Released $tag"
