#!/usr/bin/env bats
# Full-flow tests for scripts/create-release.sh (Workflow 1).

load test_helper

setup() {
    init_test_repo
}

teardown() {
    cleanup_test_repo
}

@test "create-release.sh computes the next version from the last git tag, not from pom.xml" {
    push_test_tag "v2.3.0"
    run run_script create-release.sh minor
    [ "$status" -eq 0 ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --heads origin release/2.4.x)" ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --tags origin v2.4.0)" ]
    [ "$(git -C "$TEST_REPO" rev-parse --abbrev-ref HEAD)" = "release/2.4.x" ]
    grep -q "<version>2.4.0-SNAPSHOT</version>" "$TEST_REPO/pom.xml"
    [ "$(git -C "$TEST_REPO" log -1 --pretty=%s)" = "chore(release): start release/2.4.x at 2.4.0-SNAPSHOT" ]
    [ "$(git -C "$TEST_REPO" cat-file -t v2.4.0)" = "tag" ]
}

@test "create-release.sh computes a major bump from the last tag" {
    push_test_tag "v2.3.0"
    run run_script create-release.sh major
    [ "$status" -eq 0 ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --heads origin release/3.0.x)" ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --tags origin v3.0.0)" ]
}

@test "create-release.sh falls back to release/0.1.x when no tags exist yet" {
    run run_script create-release.sh minor
    [ "$status" -eq 0 ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --heads origin release/0.1.x)" ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --tags origin v0.1.0)" ]
}

@test "create-release.sh --version overrides the automatic bump calculation" {
    push_test_tag "v2.3.0"
    run run_script create-release.sh minor --version 9.5.1
    [ "$status" -eq 0 ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --heads origin release/9.5.x)" ]
    [ -n "$(git -C "$TEST_REPO" ls-remote --tags origin v9.5.1)" ]
    grep -q "<version>9.5.1-SNAPSHOT</version>" "$TEST_REPO/pom.xml"
}

@test "create-release.sh aborts when the target branch already exists on origin" {
    (cd "$TEST_REPO" && git checkout -q -b release/5.0.x && git push -q -u origin release/5.0.x && git checkout -q main)
    run run_script create-release.sh --version 5.0.0
    [ "$status" -ne 0 ]
    [[ "$output" == *"already exists"* ]]
}

@test "create-release.sh aborts when the target tag already exists on origin" {
    push_tag_via_other_clone "v5.0.0"
    run run_script create-release.sh --version 5.0.0
    [ "$status" -ne 0 ]
    [[ "$output" == *"already exists"* ]]
}

@test "create-release.sh aborts when the working tree is dirty" {
    make_repo_dirty
    run run_script create-release.sh minor
    [ "$status" -ne 0 ]
    [[ "$output" == *"not clean"* ]]
    [ -z "$(git -C "$TEST_REPO" ls-remote --heads origin release/0.1.x)" ]
}

@test "create-release.sh aborts when not run from main" {
    (cd "$TEST_REPO" && git checkout -q -b some-other-branch)
    run run_script create-release.sh minor
    [ "$status" -ne 0 ]
    [[ "$output" == *"main"* ]]
}

@test "create-release.sh --dry-run does not modify the remote or pom.xml but reports the plan" {
    push_test_tag "v2.3.0"
    before_pom="$(cat "$TEST_REPO/pom.xml")"
    before_branch="$(git -C "$TEST_REPO" rev-parse --abbrev-ref HEAD)"
    run run_script create-release.sh minor --dry-run
    [ "$status" -eq 0 ]
    [[ "$output" == *"release/2.4.x"* ]]
    [[ "$output" == *"dry-run"* ]]
    [ -z "$(git -C "$TEST_REPO" ls-remote --heads origin release/2.4.x)" ]
    [ -z "$(git -C "$TEST_REPO" ls-remote --tags origin v2.4.0)" ]
    [ "$(cat "$TEST_REPO/pom.xml")" = "$before_pom" ]
    [ "$(git -C "$TEST_REPO" rev-parse --abbrev-ref HEAD)" = "$before_branch" ]
}
