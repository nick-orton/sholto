# CLAUDE.md

Sholto is an Android boxing-training app (Kotlin, Jetpack Compose). See
[DESIGN.md](DESIGN.md) for what it does and [PLAN.md](PLAN.md) for the
milestone build plan.

## Git workflow: gitflow

This repo uses **gitflow**.

### Branches
- `main`: released code only. Never commit to it directly.
- `develop`: the integration branch. All feature work merges here.
- `feature/<short-name>`: branched from `develop`, PR back into `develop`.
  For milestones from PLAN.md, use `feature/m<N>-<short-name>`, e.g.
  `feature/m1-round-engine`.
- `release/<version>`: branched from `develop` when preparing a release.
  Only fixes go on it. PR into `main`, then merge `main` back into `develop`.
- `hotfix/<version>`: branched from `main` for urgent fixes. PR into `main`,
  then merge `main` back into `develop`.

If `develop` doesn't exist, create it from `main` and push it.

### Commits and PRs: do these without asking
- **Commit without asking** whenever a logical unit of work is done and
  builds. Use small, focused commits with a clear message: a short summary
  line, a blank line, then the reason if it's not obvious.
- **Push and open PRs without asking** when a feature, milestone, release
  or hotfix is ready. Target the base branch gitflow calls for (usually
  `develop`). The PR description should give a summary, list what was tested,
  and link the PLAN.md milestone if there is one.
- If you're on `main` or `develop`, create the right branch before
  committing.

### Merging: always ask first
- **Never merge a PR without the user's explicit approval** in chat, even
  if CI is green. Ask, then wait for a yes.
- Never enable auto-merge, force-push to `main` or `develop`, or delete
  `main`/`develop`.
- After a release or hotfix PR is merged into `main` (with approval), ask
  before tagging the release and before merging `main` back into `develop`.
