# Working on Ichor

## Every change goes through a branch and a pull request

Never commit to `main`. For any change, however small:

1. **Start from an up-to-date `main`:** `git switch main && git pull --ff-only`.
2. **Make a branch** named for the change, short and kebab-case (`new-feature-foo`,
   `fix-bar-baz`). If the working tree already has someone else's uncommitted
   changes, leave them alone: commit only the files for this change.
3. **Make the change on the branch**, with tests. Run the full suite
4. **Commit on the branch** once the change works and the tests pass; no need to wait
   to be asked. Name each file you add; never `git add -A` or `git add .`.
5. **Push the branch and open a PR** against `main` with `gh pr create`, and give the
   user its link. The description says what was wrong, what changes and how it was
   tested. Follow-up fixes for the same change go on the same branch and PR.
6. **Merge only when the user says so**, with `gh pr merge <n> --merge` (merge commits,
   like the PRs before). Then `git switch main && git pull --ff-only` and delete the
   local branch.

A hook in `.claude/settings.json` refuses `git commit`, `merge`, `cherry-pick` and
`revert` on `main`, and any push to `main`. If it fires, make a branch and carry on there.

## Working from a GitHub issue

When the change addresses a GitHub issue, read the issue and its comments first
(`gh issue view <n> --comments`), then, before writing any code, post a comment on the
issue saying the approach you're going to take: what you understand the problem to be,
what you plan to change and where, and anything you're unsure of or leaving out
(`gh issue comment <n> --body-file <file>`). Keep it short. If the plan changes a lot
along the way, add another comment saying so. Put `Closes #<n>` in the PR description.
