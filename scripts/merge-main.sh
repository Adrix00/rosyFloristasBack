#!/usr/bin/env bash
# Workflow 3 (Release Sync): merges a release/X.Y.x branch into main.
# Pushes directly when the merge is conflict-free; opens a pull request for
# manual resolution otherwise.
#
# Usage: scripts/merge-main.sh <release/X.Y.x>
# Must be run from an already-checked-out main branch.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
# shellcheck source=scripts/utils.sh
source "${SCRIPT_DIR}/utils.sh"

open_sync_pull_request() {
    local release_branch="$1"
    local existing_pr
    existing_pr="$(gh pr list --base main --head "${release_branch}" --state open --json number --jq '.[0].number // empty')"

    if [[ -n "${existing_pr}" ]]; then
        log_info "A sync pull request for ${release_branch} already exists (#${existing_pr}); skipping."
        return 0
    fi

    gh pr create \
        --base main \
        --head "${release_branch}" \
        --title "Sync ${release_branch} into main" \
        --body "Automatic release sync produced merge conflicts. Please resolve them manually in this pull request."
}

main() {
    local release_branch="${1:-}"
    [[ -n "${release_branch}" ]] || fail "Usage: $0 <release/X.Y.x>"

    remote_branch_exists "${release_branch}" || fail "Branch '${release_branch}' does not exist on origin."

    configure_git_identity
    git fetch origin "${release_branch}"

    if git merge --no-commit --no-ff FETCH_HEAD; then
        if git diff --cached --quiet; then
            log_info "Nothing to sync; ${release_branch} is already merged into main."
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
