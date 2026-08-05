#!/usr/bin/env bash
# Workflow 3 (Release Sync): merges a release/X.Y.x branch into main.
# Pushes directly when the merge is conflict-free; opens a pull request for
# manual resolution otherwise.
#
# Usage: scripts/merge-main.sh <release/X.Y.x> [--dry-run]
# Must be run from an already-checked-out main branch.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
# shellcheck source=scripts/utils.sh
source "${SCRIPT_DIR}/utils.sh"

# Looks for an already-open sync PR before creating a new one, so retries and
# repeated conflicts don't pile up duplicate PRs. The lookup always runs for
# real (even in dry-run) since it is read-only; only the creation is guarded.
open_sync_pull_request() {
    local release_branch="$1"
    local existing_pr
    existing_pr="$(gh pr list --base main --head "${release_branch}" --state open --json number --jq '.[0].number // empty')"

    if [[ -n "${existing_pr}" ]]; then
        log_info "A sync pull request for ${release_branch} already exists (#${existing_pr}); skipping."
        return 0
    fi

    run_cmd gh pr create \
        --base main \
        --head "${release_branch}" \
        --title "Sync ${release_branch} into main" \
        --body "Automatic release sync produced merge conflicts. Please resolve them manually in this pull request."
}

main() {
    local release_branch="" dry_run=false
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dry-run) dry_run=true; shift ;;
            "") shift ;;
            *) release_branch="$1"; shift ;;
        esac
    done
    DRY_RUN="${dry_run}"

    [[ -n "${release_branch}" ]] || fail "Usage: $0 <release/X.Y.x> [--dry-run]"

    ensure_clean_worktree
    sync_remote_refs

    remote_branch_exists "${release_branch}" || fail "Branch '${release_branch}' does not exist on origin."

    configure_git_identity

    # Make sure main is fully up to date before attempting the merge.
    git checkout main
    git pull --ff-only origin main

    git fetch origin "${release_branch}"

    # This merge always runs for real -- even in dry-run -- because it's the
    # only reliable way to know whether the sync would conflict. It is always
    # undone with `git merge --abort` before returning, so the working tree is
    # left untouched regardless of the outcome or of DRY_RUN.
    if git merge --no-commit --no-ff FETCH_HEAD; then
        if git diff --cached --quiet; then
            log_info "Nothing to sync; ${release_branch} is already merged into main."
        elif [[ "${DRY_RUN}" == "true" ]]; then
            log_info "[dry-run] would run: git commit --no-edit"
            log_info "[dry-run] would run: git push origin HEAD:main"
            git merge --abort
            log_info "[dry-run] ${release_branch} would be merged into main cleanly."
        else
            git commit --no-edit
            git push origin HEAD:main
            log_success "Merged ${release_branch} into main."
        fi
    else
        git merge --abort
        log_info "Merge conflict detected; opening a pull request instead."
        open_sync_pull_request "${release_branch}"
    fi
}

main "$@"
