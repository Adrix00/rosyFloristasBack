#!/usr/bin/env bats
# Full-flow tests for scripts/merge-main.sh (Workflow 3).
#
# Each test starts from a release line created by the real create-release.sh
# and create-tag.sh (release/0.1.x with a merged bugfix and tag v0.1.1), so
# main and the release branch have genuinely diverged before merge-main.sh runs.

load test_helper

setup() {
    init_test_repo
    setup_gh_mock
    run_script create-release.sh minor
    simulate_bugfix_merge
    run_script create-tag.sh
    : > "$MOCK_GH_LOG" # discard the release-sync dispatch recorded by create-tag.sh above
}

teardown() {
    cleanup_test_repo
    cleanup_gh_mock
}

@test "merge-main.sh merges a clean release branch into main and pushes" {
    run run_script merge-main.sh release/0.1.x
    [ "$status" -eq 0 ]
    git -C "$TEST_REPO" fetch -q origin main
    run git -C "$TEST_REPO" show origin/main:BUGFIX_MARKER.txt
    [ "$status" -eq 0 ]
}

@test "merge-main.sh does nothing when the release branch is already merged into main" {
    run run_script merge-main.sh release/0.1.x
    [ "$status" -eq 0 ]
    before="$(git -C "$TEST_REPO" ls-remote --heads origin main | cut -f1)"
    run run_script merge-main.sh release/0.1.x
    [ "$status" -eq 0 ]
    [[ "$output" == *"Nothing to sync"* ]]
    after="$(git -C "$TEST_REPO" ls-remote --heads origin main | cut -f1)"
    [ "$before" = "$after" ]
}

@test "merge-main.sh opens a PR on a real conflict, leaving the tree clean" {
    push_conflicting_pom_change_to_remote_main
    run run_script merge-main.sh release/0.1.x
    [ "$status" -eq 0 ]
    [[ "$output" == *"Merge conflict detected"* ]]
    grep -q "pr create --base main --head release/0.1.x" "$MOCK_GH_LOG"
    [ -z "$(git -C "$TEST_REPO" status --porcelain)" ]
    run git -C "$TEST_REPO" rev-parse -q --verify MERGE_HEAD
    [ "$status" -ne 0 ]
}

@test "merge-main.sh does not create a duplicate PR when one is already open" {
    push_conflicting_pom_change_to_remote_main
    MOCK_GH_EXISTING_PR=42 run run_script merge-main.sh release/0.1.x
    [ "$status" -eq 0 ]
    [[ "$output" == *"already exists"* ]]
    ! grep -q "pr create" "$MOCK_GH_LOG"
}

@test "merge-main.sh picks up a commit pushed directly to main before merging" {
    push_direct_to_remote_main "concurrent-main-change"
    run run_script merge-main.sh release/0.1.x
    [ "$status" -eq 0 ]
    git -C "$TEST_REPO" fetch -q origin main
    run git -C "$TEST_REPO" show origin/main:OTHER_MARKER.txt
    [ "$status" -eq 0 ]
    run git -C "$TEST_REPO" show origin/main:BUGFIX_MARKER.txt
    [ "$status" -eq 0 ]
}

@test "merge-main.sh aborts when the release branch does not exist on origin" {
    run run_script merge-main.sh release/9.9.x
    [ "$status" -ne 0 ]
    [[ "$output" == *"does not exist"* ]]
}

@test "merge-main.sh aborts when the working tree is dirty" {
    make_repo_dirty
    run run_script merge-main.sh release/0.1.x
    [ "$status" -ne 0 ]
    [[ "$output" == *"not clean"* ]]
}

@test "merge-main.sh --dry-run on a clean merge does not push to main" {
    before="$(git -C "$TEST_REPO" ls-remote --heads origin main | cut -f1)"
    run run_script merge-main.sh release/0.1.x --dry-run
    [ "$status" -eq 0 ]
    [[ "$output" == *"dry-run"* ]]
    after="$(git -C "$TEST_REPO" ls-remote --heads origin main | cut -f1)"
    [ "$before" = "$after" ]
    [ -z "$(git -C "$TEST_REPO" status --porcelain)" ]
}

@test "merge-main.sh --dry-run on a conflict still detects it and does not create a PR" {
    push_conflicting_pom_change_to_remote_main
    run run_script merge-main.sh release/0.1.x --dry-run
    [ "$status" -eq 0 ]
    [[ "$output" == *"Merge conflict detected"* ]]
    [ -z "$(git -C "$TEST_REPO" status --porcelain)" ]
    ! grep -q "pr create" "$MOCK_GH_LOG"
}
