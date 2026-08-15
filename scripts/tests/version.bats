#!/usr/bin/env bats
# Unit tests for scripts/version.sh.

load test_helper

setup() {
    init_test_repo
    # shellcheck source=../scripts/version.sh
    source "${SCRIPTS_DIR}/version.sh"
}

teardown() {
    cleanup_test_repo
}

@test "version_major/minor/patch parse a valid version" {
    [ "$(version_major "1.2.3")" = "1" ]
    [ "$(version_minor "1.2.3-SNAPSHOT")" = "2" ]
    [ "$(version_patch "1.2.3-SNAPSHOT")" = "3" ]
}

@test "version_major fails on an invalid version" {
    run version_major "not-a-version"
    [ "$status" -ne 0 ]
}

@test "version_next_release computes a major bump and resets minor/patch" {
    result="$(version_next_release major "1.2.4-SNAPSHOT")"
    [ "$result" = "2.0.0-SNAPSHOT" ]
}

@test "version_next_release computes a minor bump and resets patch" {
    result="$(version_next_release minor "1.2.4-SNAPSHOT")"
    [ "$result" = "1.3.0-SNAPSHOT" ]
}

@test "version_next_release fails on an unknown bump type" {
    run version_next_release patch "1.2.4-SNAPSHOT"
    [ "$status" -ne 0 ]
}

@test "version_next_patch increments only the patch" {
    result="$(version_next_patch "1.3.4-SNAPSHOT")"
    [ "$result" = "1.3.5-SNAPSHOT" ]
}

@test "version_release_branch_name derives release/X.Y.x" {
    result="$(version_release_branch_name "1.3.0-SNAPSHOT")"
    [ "$result" = "release/1.3.x" ]
}

@test "version_tag_name strips -SNAPSHOT and prefixes v" {
    result="$(version_tag_name "1.3.0-SNAPSHOT")"
    [ "$result" = "v1.3.0" ]
}

@test "version_ensure_snapshot normalizes a plain version" {
    result="$(version_ensure_snapshot "2.0.0")"
    [ "$result" = "2.0.0-SNAPSHOT" ]
}

@test "version_ensure_snapshot is idempotent on an already-snapshot version" {
    result="$(version_ensure_snapshot "2.0.0-SNAPSHOT")"
    [ "$result" = "2.0.0-SNAPSHOT" ]
}

@test "version_ensure_snapshot fails on an invalid version" {
    run version_ensure_snapshot "not-a-version"
    [ "$status" -ne 0 ]
}

@test "version_last_tag_overall falls back to v0.0.0 when no tags exist" {
    cd "$TEST_REPO"
    result="$(version_last_tag_overall)"
    [ "$result" = "v0.0.0" ]
}

@test "version_last_tag_overall returns the numerically highest tag across the repo" {
    push_test_tag "v1.2.0"
    push_test_tag "v1.10.0"
    push_test_tag "v1.3.0"
    cd "$TEST_REPO"
    result="$(version_last_tag_overall)"
    [ "$result" = "v1.10.0" ]
}

@test "version_last_tag_for_branch finds the highest tag for that release line only" {
    push_test_tag "v1.3.0"
    push_test_tag "v1.3.4"
    push_test_tag "v1.4.0"
    cd "$TEST_REPO"
    result="$(version_last_tag_for_branch "release/1.3.x")"
    [ "$result" = "v1.3.4" ]
}

@test "version_last_tag_for_branch fails on a malformed branch name" {
    cd "$TEST_REPO"
    run version_last_tag_for_branch "not-a-release-branch"
    [ "$status" -ne 0 ]
}

@test "version_read_current (legacy) still reads the version from pom.xml via Maven" {
    cd "$TEST_REPO"
    result="$(version_read_current)"
    [ "$result" = "0.0.1-SNAPSHOT" ]
}
