#!/usr/bin/env bash
# Usage: CLOJARS_USERNAME=... CLOJARS_PASSWORD=<deploy token> ./release.sh major|minor|patch
#
# Commit the "Prepare <version>" first: date the [Unreleased] section of
# CHANGELOG.md as the version to come, and install that version in README.md and
# doc/getting-started.md. The pom points to the tag and cljdoc builds its docs
# from it, so the docs have to be ready in the commit the tag lands on.
#
# Without the credentials, the script checks that and stops before it tags.
set -e

# The version to come: metav bumps the nearest tag
last=$(git describe --tags --abbrev=0 --match 'v*')
IFS=. read -r major minor patch <<< "${last#v}"
case "$1" in
  major) next="$((major + 1)).0.0" ;;
  minor) next="$major.$((minor + 1)).0" ;;
  patch) next="$major.$minor.$((patch + 1))" ;;
  *) echo "Usage: ./release.sh major|minor|patch" >&2; exit 1 ;;
esac

ready=true
grep -qE "^# \[${next//./\\.}\] - [0-9]{4}-[0-9]{2}-[0-9]{2}" CHANGELOG.md \
  || { echo "CHANGELOG.md has no dated section for $next" >&2; ready=false; }
for doc in README.md doc/getting-started.md; do
  grep -qF "\"$next\"" "$doc" \
    || { echo "$doc does not install $next" >&2; ready=false; }
done
# what was checked is the working tree, the tag lands on a commit
[ -z "$(git status --porcelain)" ] \
  || { echo "There are uncommitted changes" >&2; ready=false; }
$ready || { echo "Nothing tagged: commit the \"Prepare $next\" first." >&2; exit 1; }
echo "Docs are ready for $next"

: "${CLOJARS_USERNAME:?CLOJARS_USERNAME is not set}"
: "${CLOJARS_PASSWORD:?CLOJARS_PASSWORD is not set}"

# Bump the version, spit it into src/scenari/meta.clj, commit and tag (no push yet).
# metav prints its SemVer records before the tag: keep the last line only
tag=$(clj -M:release "$1" --spit --output-dir src --namespace scenari.meta --formats clj --without-push | tail -1)
echo "Tagged $tag"

[ "$tag" = "v$next" ] \
  || { echo "Expected v$next: nothing published, the tag and its commit are local." >&2; exit 1; }

./build.sh
./deploy.sh

# Push only once Clojars accepted the artifact
git push origin HEAD "$tag"
echo "Released $tag"
