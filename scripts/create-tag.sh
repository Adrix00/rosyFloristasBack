#!/usr/bin/env bash
# Workflow 2 (Release Tagging): after a bugfix/hotfix PR is merged into a
# release/X.Y.x branch, bumps the patch version, cuts the next tag and
# triggers the Release Sync workflow.
#
# Usage: scripts/create-tag.sh
# Must be run from an already-checked-out release/X.Y.x branch.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
# shellcheck source=scripts/utils.sh
source "${SCRIPT_DIR}/utils.sh"
# shellcheck source=scripts/version.sh
source "${SCRIPT_DIR}/version.sh"

main() {
    local release_branch
    release_branch="$(current_branch)"
    [[ "${release_branch}" =~ ^release/[0-9]+\.[0-9]+\.x$ ]] \
        || fail "create-tag.sh must run from a 'release/X.Y.x' branch, not '${release_branch}'."

    local last_tag
    last_tag="$(version_last_tag_for_branch "${release_branch}")"
    [[ -n "${last_tag}" ]] || fail "No existing tag found for ${release_branch}. Run Create Release first."
    log_info "Last tag for ${release_branch}: ${last_tag}"

    local last_version new_version new_tag
    last_version="${last_tag#v}"
    new_version="$(version_next_patch "${last_version}")"
    new_tag="$(version_tag_name "${new_version}")"
    log_info "Next patch: ${new_version}, tag ${new_tag}"

    remote_tag_exists "${new_tag}" && fail "Tag '${new_tag}' already exists on origin."

    configure_git_identity

    ./mvnw -q -B versions:set -DnewVersion="${new_version}" -DgenerateBackupPoms=false
    commit_pom_version_bump "chore(release): bump version to ${new_version}"

    push_branch_and_tag "${release_branch}" "${new_tag}"

    trigger_release_sync "${release_branch}"

    log_success "Tagged ${new_tag} on ${release_branch} and triggered release sync."
}

main "$@"
