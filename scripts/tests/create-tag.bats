#!/usr/bin/env bats
# Full-flow tests for scripts/create-tag.sh (Workflow 2).
#
# Each test starts from a release line already created by the real
# create-release.sh (release/0.1.x + tag v0.1.0), with a merged bugfix branch
# on top, mirroring exactly what release-tagging.yml hands off to this script.

load test_helper

setup() {
    init_test_repo
    setup_gh_mock
    run_script create-release.sh minor
}

teardown() {
    cleanup_test_repo
    cleanup_gh_mock
}

@test "create-tag.sh increments the patch and updates pom/commit/tag" {
    simulate_bugfix_merge
    run run_script create-tag.sh
    [ "$status" -eq 0 ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --tags origin v0.1.1)" ]
    grep -q "<version>0.1.1-SNAPSHOT</version>" "$TEST_REPO/pom.xml"
    [ "$(git -C "$TEST_REPO" log -1 --pretty=%s)" = "chore(release): bump version to 0.1.1-SNAPSHOT" ]
    [ "$(git -C "$TEST_REPO" cat-file -t v0.1.1)" = "tag" ]
}

@test "create-tag.sh triggers release-sync.yml via gh workflow run" {
    simulate_bugfix_merge
    run run_script create-tag.sh
    [ "$status" -eq 0 ]
    grep -q "workflow run release-sync.yml --ref main -f release_branch=release/0.1.x" "$MOCK_GH_LOG"
}

@test "create-tag.sh aborts when no prior tag exists for the release line" {
    git -C "$TEST_REPO" tag -d v0.1.0
    git -C "$TEST_REPO" push -q origin :refs/tags/v0.1.0
    run run_script create-tag.sh
    [ "$status" -ne 0 ]
    [[ "$output" == *"No existing tag found"* ]]
}

@test "create-tag.sh picks up a tag pushed concurrently instead of colliding with it" {
    # Because create-tag.sh always re-syncs tags before computing the next
    # patch (sync_remote_refs), a tag pushed concurrently is picked up as the
    # new "last tag" rather than being collided with -- self-healing by design.
    simulate_bugfix_merge
    push_tag_via_other_clone "v0.1.1"
    run run_script create-tag.sh
    [ "$status" -eq 0 ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --tags origin v0.1.2)" ]
}

@test "create-tag.sh aborts when the working tree is dirty" {
    make_repo_dirty
    run run_script create-tag.sh
    [ "$status" -ne 0 ]
    [[ "$output" == *"not clean"* ]]
}

@test "create-tag.sh aborts when not run from a release/X.Y.x branch" {
    checkout_test_branch main
    run run_script create-tag.sh
    [ "$status" -ne 0 ]
    [[ "$output" == *"release/X.Y.x"* ]]
}

@test "create-tag.sh --dry-run does not modify the remote or pom.xml, and does not trigger release-sync" {
    simulate_bugfix_merge
    before_pom="$(cat "$TEST_REPO/pom.xml")"
    run run_script create-tag.sh --dry-run
    [ "$status" -eq 0 ]
    [[ "$output" == *"dry-run"* ]]
    [ -z "$(git -C "$TEST_REPO" ls-remote --tags origin v0.1.1)" ]
    [ "$(cat "$TEST_REPO/pom.xml")" = "$before_pom" ]
    [ ! -s "$MOCK_GH_LOG" ]
}
