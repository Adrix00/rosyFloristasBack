#!/usr/bin/env bats
# Unit tests for scripts/utils.sh.

load test_helper

setup() {
    init_test_repo
    # shellcheck source=../scripts/utils.sh
    source "${SCRIPTS_DIR}/utils.sh"
}

teardown() {
    cleanup_test_repo
}

@test "ensure_clean_worktree passes on a clean repo" {
    cd "$TEST_REPO"
    run ensure_clean_worktree
    [ "$status" -eq 0 ]
}

@test "ensure_clean_worktree fails when there are untracked files" {
    cd "$TEST_REPO"
    make_repo_dirty
    run ensure_clean_worktree
    [ "$status" -ne 0 ]
}

@test "ensure_clean_worktree fails when there are unstaged modifications" {
    cd "$TEST_REPO"
    echo "modified" >> pom.xml
    run ensure_clean_worktree
    [ "$status" -ne 0 ]
}

@test "ensure_clean_worktree fails when there are staged changes" {
    cd "$TEST_REPO"
    echo "modified" >> pom.xml
    git add pom.xml
    run ensure_clean_worktree
    [ "$status" -ne 0 ]
}

@test "remote_branch_exists detects an existing remote branch" {
    cd "$TEST_REPO"
    run remote_branch_exists "main"
    [ "$status" -eq 0 ]
}

@test "remote_branch_exists returns false for a missing branch" {
    cd "$TEST_REPO"
    run remote_branch_exists "does-not-exist"
    [ "$status" -ne 0 ]
}

@test "remote_tag_exists detects an existing remote tag" {
    push_test_tag "v1.0.0"
    cd "$TEST_REPO"
    run remote_tag_exists "v1.0.0"
    [ "$status" -eq 0 ]
}

@test "remote_tag_exists returns false for a missing tag" {
    cd "$TEST_REPO"
    run remote_tag_exists "v9.9.9"
    [ "$status" -ne 0 ]
}

@test "sync_remote_refs updates local knowledge of origin/main after a concurrent push" {
    cd "$TEST_REPO"
    before="$(git rev-parse origin/main)"
    push_direct_to_remote_main "concurrent"
    sync_remote_refs
    after="$(git rev-parse origin/main)"
    [ "$before" != "$after" ]
}

@test "sync_remote_refs fetches a tag pushed directly to the remote" {
    cd "$TEST_REPO"
    run git rev-parse v5.5.5
    [ "$status" -ne 0 ]
    push_tag_via_other_clone "v5.5.5"
    sync_remote_refs
    run git rev-parse v5.5.5
    [ "$status" -eq 0 ]
}

@test "run_cmd executes the command when DRY_RUN is false" {
    cd "$TEST_REPO"
    DRY_RUN=false run_cmd touch MARKER.txt
    [ -f MARKER.txt ]
}

@test "run_cmd does not execute the command when DRY_RUN is true" {
    cd "$TEST_REPO"
    DRY_RUN=true run_cmd touch MARKER.txt
    [ ! -f MARKER.txt ]
}

@test "configure_git_identity sets the github-actions bot identity" {
    cd "$TEST_REPO"
    configure_git_identity
    [ "$(git config user.name)" = "github-actions[bot]" ]
    [ "$(git config user.email)" = "41898282+github-actions[bot]@users.noreply.github.com" ]
}

@test "commit_pom_version_bump stages pom.xml and commits with the given message" {
    cd "$TEST_REPO"
    configure_git_identity
    echo "<!-- modified -->" >> pom.xml
    commit_pom_version_bump "chore(release): test bump"
    run git log -1 --pretty=%s
    [ "$output" = "chore(release): test bump" ]
}

@test "commit_pom_version_bump in dry-run does not commit" {
    cd "$TEST_REPO"
    configure_git_identity
    before="$(git rev-parse HEAD)"
    echo "<!-- modified -->" >> pom.xml
    DRY_RUN=true commit_pom_version_bump "chore(release): test bump"
    after="$(git rev-parse HEAD)"
    [ "$before" = "$after" ]
}

@test "push_branch_and_tag creates an annotated tag and pushes branch+tag atomically" {
    cd "$TEST_REPO"
    configure_git_identity
    push_branch_and_tag "main" "v9.9.9" "Release v9.9.9"
    [ "$(git cat-file -t v9.9.9)" = "tag" ]
    [ -n "$(git ls-remote --tags origin v9.9.9)" ]
}

@test "push_branch_and_tag in dry-run does not create a local tag or touch the remote" {
    cd "$TEST_REPO"
    configure_git_identity
    DRY_RUN=true push_branch_and_tag "main" "v9.9.9" "Release v9.9.9"
    run git rev-parse v9.9.9
    [ "$status" -ne 0 ]
    [ -z "$(git ls-remote --tags origin v9.9.9)" ]
}
