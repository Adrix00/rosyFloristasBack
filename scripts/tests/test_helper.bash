#!/usr/bin/env bash
# Shared setup/teardown helpers for the release-scripts Bats suite.
# Loaded via `load test_helper` from each .bats file. Every test gets its own
# isolated Git repo + bare "origin" remote under the system temp directory;
# nothing here ever touches the real project repo. Maven runs for real
# (the real ./mvnw + .mvn wrapper are copied into each fixture repo); only
# `gh` is mocked, via scripts/tests/fixtures/bin/gh prepended to PATH.

set -euo pipefail

SCRIPTS_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." &>/dev/null && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPTS_DIR}/.." &>/dev/null && pwd)"
FIXTURES_DIR="${SCRIPTS_DIR}/tests/fixtures"

# Creates $TEST_REPO (a working copy) and $TEST_REMOTE (its bare "origin"),
# seeded with the real mvnw/.mvn wrapper and a minimal fixture pom.xml, with
# one initial commit on `main`.
init_test_repo() {
    TEST_REPO="$(mktemp -d "${TMPDIR:-/tmp}/rosy-release-test.XXXXXX")"
    TEST_REMOTE="$(mktemp -d "${TMPDIR:-/tmp}/rosy-release-remote.XXXXXX")"
    export TEST_REPO TEST_REMOTE

    git init --bare -q -b main "${TEST_REMOTE}"
    git init -q -b main "${TEST_REPO}"

    cp "${FIXTURES_DIR}/pom.xml.template" "${TEST_REPO}/pom.xml"
    cp "${PROJECT_ROOT}/mvnw" "${TEST_REPO}/mvnw"
    cp -R "${PROJECT_ROOT}/.mvn" "${TEST_REPO}/.mvn"
    chmod +x "${TEST_REPO}/mvnw"

    (
        cd "${TEST_REPO}"
        git config user.name "Test User"
        git config user.email "test@example.invalid"
        git add -A
        git commit -q -m "chore: initial fixture commit"
        git branch -m main
        git remote add origin "${TEST_REMOTE}"
        git push -q -u origin main
    )
}

cleanup_test_repo() {
    [[ -n "${TEST_REPO:-}" ]] && rm -rf "${TEST_REPO}"
    [[ -n "${TEST_REMOTE:-}" ]] && rm -rf "${TEST_REMOTE}"
}

# Prepends the mock `gh` to PATH and points MOCK_GH_LOG at a fresh temp file
# so tests can assert exactly which `gh` invocations happened.
setup_gh_mock() {
    PATH="${FIXTURES_DIR}/bin:${PATH}"
    export PATH
    MOCK_GH_LOG="$(mktemp "${TMPDIR:-/tmp}/rosy-mock-gh-log.XXXXXX")"
    export MOCK_GH_LOG
}

cleanup_gh_mock() {
    [[ -n "${MOCK_GH_LOG:-}" ]] && rm -f "${MOCK_GH_LOG}"
    unset MOCK_GH_EXISTING_PR || true
}

# Runs a script under test with $TEST_REPO as CWD. Intended to be invoked via
# Bats' own `run`, e.g.: run run_script create-release.sh minor --dry-run
run_script() {
    local script="$1"
    shift
    (cd "${TEST_REPO}" && "${SCRIPTS_DIR}/${script}" "$@")
}

current_test_branch() {
    (cd "${TEST_REPO}" && git rev-parse --abbrev-ref HEAD)
}

checkout_test_branch() {
    (cd "${TEST_REPO}" && git checkout -q "$1")
}

# Creates an annotated tag directly at $TEST_REPO's current HEAD and pushes
# it, bypassing the scripts under test -- used to seed pre-existing state.
push_test_tag() {
    local tag="$1"
    (cd "${TEST_REPO}" && git tag -a "${tag}" -m "Release ${tag}" && git push -q origin "${tag}")
}

make_repo_dirty() {
    echo "uncommitted change" >> "${TEST_REPO}/DIRTY.txt"
}

# Simulates a bugfix/hotfix branch created off the current branch, with a
# one-line change, merged back in with --no-ff and pushed -- exactly what a
# merged PR looks like from create-tag.sh's point of view.
simulate_bugfix_merge() {
    local base_branch
    base_branch="$(current_test_branch)"
    (
        cd "${TEST_REPO}"
        git checkout -q -b "bugfix/test-fix"
        echo "fix marker $$-${RANDOM}" > "BUGFIX_MARKER.txt"
        git add -A
        git commit -q -m "fix: test bugfix"
        git checkout -q "${base_branch}"
        git merge -q --no-ff bugfix/test-fix -m "Merge pull request from bugfix/test-fix"
        git push -q origin "${base_branch}"
    )
}

# Pushes a commit directly to $TEST_REMOTE's main via a throwaway clone,
# bypassing $TEST_REPO's own working copy -- simulates a concurrent change
# landing on main from elsewhere.
push_direct_to_remote_main() {
    local other_clone
    other_clone="$(mktemp -d "${TMPDIR:-/tmp}/rosy-release-other.XXXXXX")"
    git clone -q "${TEST_REMOTE}" "${other_clone}"
    (
        cd "${other_clone}"
        git config user.name "Other User"
        git config user.email "other@example.invalid"
        echo "concurrent change $$-${RANDOM}" >> OTHER_MARKER.txt
        git add -A
        git commit -q -m "chore: concurrent main change"
        git push -q origin main
    )
    rm -rf "${other_clone}"
}

# Pushes a tag directly to $TEST_REMOTE via a throwaway clone, at whatever
# commit main currently points to -- simulates a tag created elsewhere.
push_tag_via_other_clone() {
    local tag="$1"
    local other_clone
    other_clone="$(mktemp -d "${TMPDIR:-/tmp}/rosy-release-other.XXXXXX")"
    git clone -q "${TEST_REMOTE}" "${other_clone}"
    (
        cd "${other_clone}"
        git config user.name "Other User"
        git config user.email "other@example.invalid"
        git tag -a "${tag}" -m "Release ${tag}"
        git push -q origin "${tag}"
    )
    rm -rf "${other_clone}"
}

# Same as above, but edits pom.xml's <version> so it collides with whatever a
# release branch under test also changed -- used to force a real merge conflict.
push_conflicting_pom_change_to_remote_main() {
    local other_clone
    other_clone="$(mktemp -d "${TMPDIR:-/tmp}/rosy-release-other.XXXXXX")"
    git clone -q "${TEST_REMOTE}" "${other_clone}"
    (
        cd "${other_clone}"
        git config user.name "Other User"
        git config user.email "other@example.invalid"
        sed -i.bak -E 's#<version>[^<]*</version>#<version>9.9.9-CONFLICT</version>#' pom.xml
        rm -f pom.xml.bak
        git add -A
        git commit -q -m "chore: conflicting pom change on main"
        git push -q origin main
    )
    rm -rf "${other_clone}"
}
