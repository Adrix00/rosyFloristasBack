#!/usr/bin/env bash
# Shared helpers sourced by the other release scripts.
# This file is a function library; it is not meant to be executed directly.

set -euo pipefail

# Global dry-run switch. Scripts flip this to "true" when invoked with --dry-run;
# every mutating operation goes through run_cmd() so it is skipped consistently.
: "${DRY_RUN:=false}"

log_info() {
    printf '\033[0;34m[INFO]\033[0m %s\n' "$1"
}

log_success() {
    printf '\033[0;32m[OK]\033[0m %s\n' "$1"
}

log_error() {
    printf '\033[0;31m[ERROR]\033[0m %s\n' "$1" >&2
}

fail() {
    log_error "$1"
    exit 1
}

# Executes "$@" unless DRY_RUN is "true", in which case it only logs what would
# have run. Named run_cmd (not "run") to avoid colliding with Bats' own `run`
# helper when this file is sourced directly inside a .bats test.
run_cmd() {
    if [[ "${DRY_RUN}" == "true" ]]; then
        log_info "[dry-run] would run: $*"
    else
        "$@"
    fi
}

# Configures the committer identity used for every automated commit/tag.
# Uses the canonical github-actions[bot] identity, matching GitHub's own bot user.
configure_git_identity() {
    git config user.name "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
}

current_branch() {
    git rev-parse --abbrev-ref HEAD
}

# Aborts unless the working tree (staged, unstaged and untracked) is fully clean.
# Must run before any other operation, per design.
ensure_clean_worktree() {
    [[ -z "$(git status --porcelain)" ]] || fail "Working tree is not clean. Commit or stash pending changes before running this script."
}

# Refreshes local branches and tags from origin, pruning stale refs, so every
# subsequent branch/tag lookup reflects the latest remote state.
sync_remote_refs() {
    git fetch --tags --prune origin
}

remote_branch_exists() {
    local branch="$1"
    [[ -n "$(git ls-remote --heads origin "${branch}")" ]]
}

remote_tag_exists() {
    local tag="$1"
    [[ -n "$(git ls-remote --tags origin "${tag}")" ]]
}

# Stages and commits the version bump in pom.xml produced by `mvn versions:set`.
commit_pom_version_bump() {
    local message="$1"
    run_cmd git add pom.xml
    run_cmd git commit -m "${message}"
}

# Creates an annotated tag on HEAD and pushes the branch (by name) and the tag
# together, atomically, so a partial network failure can never leave a pushed
# branch without its tag.
push_branch_and_tag() {
    local branch="$1"
    local tag="$2"
    local message="$3"
    run_cmd git tag -a "${tag}" -m "${message}"
    run_cmd git push --atomic origin "HEAD:refs/heads/${branch}" "refs/tags/${tag}"
}

# Dispatches the Release Sync workflow for the given release branch.
# Uses `gh` (workflow_dispatch), which -- unlike a plain `git push` made with
# GITHUB_TOKEN -- is explicitly allowed to trigger a new workflow run.
trigger_release_sync() {
    local release_branch="$1"
    log_info "Dispatching release-sync.yml for ${release_branch}"
    run_cmd gh workflow run release-sync.yml --ref main -f "release_branch=${release_branch}"
}
