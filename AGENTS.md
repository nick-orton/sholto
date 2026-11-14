# AGENTS.md

Instructions for any coding agent working in this repo (Antigravity, Claude
Code, etc.). `CLAUDE.md` imports this file; edit rules here.

Sholto is an Android boxing-training app (Kotlin, Jetpack Compose). See
[DESIGN.md](DESIGN.md) for what it does and [PLAN.md](PLAN.md) for the
milestone build plan.

## Git workflow: gitflow, with milestones and issues released to `main`

Development follows gitflow. **At the end of every milestone or issue
implementation, a PR is opened against `main` and merged** (after the
user's approval, see below).

### Branches
- `main`: released code. Each completed milestone or issue lands here.
  Never commit to it directly.
- `develop`: the integration branch. It is kept in sync with `main` after
  every merge.
- `feature/m<N>-<short-name>`: one branch per PLAN.md milestone, branched
  from `develop`. Example: `feature/m1-round-engine`.
- `feature/<issue#>-<short-name>`: one branch per GitHub issue, branched
  from `develop`. Example: `feature/12-pause-button`. Finished like a
  milestone (PR into `main`).
- `feature/<short-name>`: small work outside a milestone or issue (docs, tweaks).
  Branched from `develop`, PR into `develop`. It reaches `main` with the
  next milestone or issue PR, because those branches start from `develop`.
- `hotfix/<short-name>`: branched from `main` for urgent fixes. PR into
  `main`.

If `develop` doesn't exist, create it from `main` and push it.

### Finishing a milestone or issue
When a milestone's "Done when" checks pass, or an issue is implemented:
1. Commit and push the `feature/m<N>-…` or `feature/<issue#>-…` branch.
2. **Open the PR with base `main`, not `develop`.** Title it
   `M<N>: <milestone name>` or `<issue title> (#<issue#>)`. The description
   gives a summary and lists what was tested. Milestone PRs link the
   milestone in PLAN.md; issue PRs include `Closes #<issue#>`.
3. Ask the user to approve the merge. Wait for an explicit yes.
4. After merging, **sync `develop` with `main`** without asking: merge
   `main` into `develop` (fast-forward when possible) and push. The user
   should never have to merge `develop` into `main` by hand.
   If `develop` is checked out in another worktree, push `origin/main` to
   `develop` directly (`git push origin origin/main:refs/heads/develop`,
   which only works as a fast-forward).
5. Then **update the local copies** without asking:
   - `git fetch origin`.
   - For each local `main` and `develop`: if it's checked out in a worktree
     (see `git worktree list`), run `git -C <that worktree> merge --ff-only
     origin/<branch>` when that worktree has no uncommitted changes. If
     it's not checked out anywhere, run `git branch -f <branch>
     origin/<branch>`.
   - Never discard local changes or commits to do this. If a fast-forward
     isn't possible, tell the user which copy is behind and why.
   - Tell the user if the main checkout is on a branch other than `main`
     or `develop`, so they know `git pull` there won't pick up the merge.

The same sync happens after a hotfix PR is merged into `main`.

### Do these without asking
- **Commit** whenever a logical unit of work is done and builds. Use small,
  focused commits with a clear message: a short summary line, a blank line,
  then the reason if it's not obvious.
- **Push and open PRs** with the base branch from the rules above.
- **Sync `develop` with `main`** after a PR into `main` is merged.
- If you're on `main` or `develop`, create the right branch before
  committing.

### Always ask first
- **Never merge a PR without the user's explicit approval** in chat, even
  if CI is green. Ask, then wait for a yes.
- Ask before tagging a release.
- Never enable auto-merge, force-push to `main` or `develop`, or delete
  `main`/`develop`.
