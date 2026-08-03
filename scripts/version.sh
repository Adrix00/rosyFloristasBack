#!/usr/bin/env bash
# Version helpers: read the current Maven project version and compute the
# next release/patch version, branch name and tag name from it.
#
# Can be sourced as a function library by other scripts, or invoked directly:
#   scripts/version.sh current
#   scripts/version.sh next-release <major|minor> <version>
#   scripts/version.sh next-patch <version>
#   scripts/version.sh branch-name <version>
#   scripts/version.sh tag-name <version>
#   scripts/version.sh last-tag-for-branch <release/X.Y.x>
#
# Must be run from the repository root (relies on ./mvnw and the local git tags).

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
# shellcheck source=scripts/utils.sh
source "${SCRIPT_DIR}/utils.sh"

readonly SEMVER_REGEX='^([0-9]+)\.([0-9]+)\.([0-9]+)(-SNAPSHOT)?$'

version_read_current() {
    ./mvnw -q -B help:evaluate -Dexpression=project.version -DforceStdout
}

version_strip_snapshot() {
    local version="$1"
    echo "${version%-SNAPSHOT}"
}

# Validates $1 and populates BASH_REMATCH with its major/minor/patch groups.
version_parse() {
    local version="$1"
    local core
    core="$(version_strip_snapshot "${version}")"
    [[ "${core}" =~ ${SEMVER_REGEX} ]] || fail "Not a valid semantic version: ${version}"
}

version_major() {
    version_parse "$1"
    echo "${BASH_REMATCH[1]}"
}

version_minor() {
    version_parse "$1"
    echo "${BASH_REMATCH[2]}"
}

version_patch() {
    version_parse "$1"
    echo "${BASH_REMATCH[3]}"
}

# Computes the first -SNAPSHOT version of a new release line.
# major: 1.2.4 -> 2.0.0-SNAPSHOT   minor: 1.2.4 -> 1.3.0-SNAPSHOT
version_next_release() {
    local bump="$1"
    local current="$2"
    local major minor
    major="$(version_major "${current}")"
    minor="$(version_minor "${current}")"

    case "${bump}" in
        major)
            echo "$((major + 1)).0.0-SNAPSHOT"
            ;;
        minor)
            echo "${major}.$((minor + 1)).0-SNAPSHOT"
            ;;
        *)
            fail "Unknown bump type: ${bump} (expected 'major' or 'minor')"
            ;;
    esac
}

# Computes the next patch -SNAPSHOT version: 1.3.4 -> 1.3.5-SNAPSHOT
version_next_patch() {
    local current="$1"
    local major minor patch
    major="$(version_major "${current}")"
    minor="$(version_minor "${current}")"
    patch="$(version_patch "${current}")"
    echo "${major}.${minor}.$((patch + 1))-SNAPSHOT"
}

version_release_branch_name() {
    local version="$1"
    local major minor
    major="$(version_major "${version}")"
    minor="$(version_minor "${version}")"
    echo "release/${major}.${minor}.x"
}

version_tag_name() {
    local version="$1"
    local core
    core="$(version_strip_snapshot "${version}")"
    echo "v${core}"
}

# Finds the highest existing vX.Y.* tag for a release/X.Y.x branch.
# Requires tags to have been fetched locally (fetch-depth: 0 in CI).
version_last_tag_for_branch() {
    local release_branch="$1"
    local major minor
    if [[ "${release_branch}" =~ ^release/([0-9]+)\.([0-9]+)\.x$ ]]; then
        major="${BASH_REMATCH[1]}"
        minor="${BASH_REMATCH[2]}"
    else
        fail "Not a valid release branch name: ${release_branch}"
    fi
    git tag -l "v${major}.${minor}.*" | sort -V | tail -n 1
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    command="${1:-}"
    [[ -n "${command}" ]] || fail "Usage: $0 {current|next-release <major|minor> <version>|next-patch <version>|branch-name <version>|tag-name <version>|last-tag-for-branch <release/X.Y.x>}"
    shift

    case "${command}" in
        current) version_read_current ;;
        next-release) version_next_release "$1" "$2" ;;
        next-patch) version_next_patch "$1" ;;
        branch-name) version_release_branch_name "$1" ;;
        tag-name) version_tag_name "$1" ;;
        last-tag-for-branch) version_last_tag_for_branch "$1" ;;
        *) fail "Unknown command: ${command}" ;;
    esac
fi
