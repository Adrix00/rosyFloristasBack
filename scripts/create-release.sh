#!/usr/bin/env bash
# Workflow 1 (Create Release): cuts a new release/X.Y.x branch from main,
# sets its pom.xml to X.Y.0-SNAPSHOT and pushes the first tag of that line.
#
# The next version is computed from the highest vX.Y.Z tag in the repo (the
# source of truth), unless an explicit version is given via --version, which
# always takes priority over --dry-run.
#
# Usage: scripts/create-release.sh [major|minor] [--version X.Y.Z] [--dry-run]
# Must be run from an already-checked-out main branch.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
# shellcheck source=scripts/utils.sh
source "${SCRIPT_DIR}/utils.sh"
# shellcheck source=scripts/version.sh
source "${SCRIPT_DIR}/version.sh"

main() {
    local bump="" explicit_version="" dry_run=false
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dry-run) dry_run=true; shift ;;
            --version) explicit_version="${2:-}"; shift 2 ;;
            major | minor) bump="$1"; shift ;;
            "") shift ;;
            *) fail "Unknown argument: $1" ;;
        esac
    done
    DRY_RUN="${dry_run}"

    [[ -n "${bump}" || -n "${explicit_version}" ]] \
        || fail "Usage: $0 [major|minor] [--version X.Y.Z] [--dry-run]"

    ensure_clean_worktree

    local branch
    branch="$(current_branch)"
    [[ "${branch}" == "main" ]] || fail "Create Release must run from 'main', not '${branch}'."

    sync_remote_refs

    local new_version
    if [[ -n "${explicit_version}" ]]; then
        [[ -z "${bump}" ]] || log_info "Both a bump type and --version were given; using --version and ignoring the bump type."
        new_version="$(version_ensure_snapshot "${explicit_version}")"
        log_info "Using explicit version override: ${new_version}"
    else
        local last_tag base_version
        last_tag="$(version_last_tag_overall)"
        base_version="${last_tag#v}"
        log_info "Last known tag: ${last_tag}"
        new_version="$(version_next_release "${bump}" "${base_version}")"
    fi

    local release_branch first_tag
    release_branch="$(version_release_branch_name "${new_version}")"
    first_tag="$(version_tag_name "${new_version}")"
    log_info "Next release: ${new_version} -> ${release_branch}, first tag ${first_tag}"

    remote_branch_exists "${release_branch}" && fail "Branch '${release_branch}' already exists on origin."
    remote_tag_exists "${first_tag}" && fail "Tag '${first_tag}' already exists on origin."

    configure_git_identity
    run_cmd git checkout -b "${release_branch}"

    run_cmd ./mvnw -q -B versions:set -DnewVersion="${new_version}" -DgenerateBackupPoms=false
    commit_pom_version_bump "chore(release): start ${release_branch} at ${new_version}"

    push_branch_and_tag "${release_branch}" "${first_tag}" "Release ${first_tag}"

    log_success "Created ${release_branch} and tag ${first_tag}."
}

main "$@"
