#!/usr/bin/env bash
# Workflow 1 (Create Release): cuts a new release/X.Y.x branch from main,
# sets its pom.xml to X.Y.0-SNAPSHOT and pushes the first tag of that line.
#
# Usage: scripts/create-release.sh <major|minor>
# Must be run from an already-checked-out main branch.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
# shellcheck source=scripts/utils.sh
source "${SCRIPT_DIR}/utils.sh"
# shellcheck source=scripts/version.sh
source "${SCRIPT_DIR}/version.sh"

main() {
    local bump="${1:-}"
    [[ "${bump}" == "major" || "${bump}" == "minor" ]] || fail "Usage: $0 <major|minor>"

    local branch
    branch="$(current_branch)"
    [[ "${branch}" == "main" ]] || fail "Create Release must run from 'main', not '${branch}'."

    local current_version new_version release_branch first_tag
    current_version="$(version_read_current)"
    log_info "Current version on main: ${current_version}"

    new_version="$(version_next_release "${bump}" "${current_version}")"
    release_branch="$(version_release_branch_name "${new_version}")"
    first_tag="$(version_tag_name "${new_version}")"
    log_info "Next release: ${new_version} -> ${release_branch}, first tag ${first_tag}"

    remote_branch_exists "${release_branch}" && fail "Branch '${release_branch}' already exists on origin."
    remote_tag_exists "${first_tag}" && fail "Tag '${first_tag}' already exists on origin."

    configure_git_identity
    git checkout -b "${release_branch}"

    ./mvnw -q -B versions:set -DnewVersion="${new_version}" -DgenerateBackupPoms=false
    commit_pom_version_bump "chore(release): start ${release_branch} at ${new_version}"

    push_branch_and_tag "${release_branch}" "${first_tag}"

    log_success "Created ${release_branch} and tag ${first_tag}."
}

main "$@"
