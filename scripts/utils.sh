#!/usr/bin/env bash
# Shared helpers sourced by the other release scripts.
# This file is a function library; it is not meant to be executed directly.

set -euo pipefail

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

# Configures the committer identity used for every automated commit/tag.
# Uses the canonical github-actions[bot] identity, matching GitHub's own bot user.
configure_git_identity() {
    git config user.name "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
}

current_branch() {
    git rev-parse --abbrev-ref HEAD
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
    git add pom.xml
    git commit -m "${message}"
}

# Tags HEAD and pushes the branch (by name) and the tag together, atomically,
# so a partial network failure can never leave a pushed branch without its tag.
push_branch_and_tag() {
    local branch="$1"
    local tag="$2"
    git tag "${tag}"
    git push --atomic origin "HEAD:refs/heads/${branch}" "refs/tags/${tag}"
}

# Dispatches the Release Sync workflow for the given release branch.
# Uses `gh` (workflow_dispatch), which -- unlike a plain `git push` made with
# GITHUB_TOKEN -- is explicitly allowed to trigger a new workflow run.
trigger_release_sync() {
    local release_branch="$1"
    log_info "Dispatching release-sync.yml for ${release_branch}"
    gh workflow run release-sync.yml --ref main -f "release_branch=${release_branch}"
}
