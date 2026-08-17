# Contributing

Thank you for your interest in contributing! This document describes the
workflow, conventions, and expectations for contributing to this project.

## Picking What to Work On

Curious what's planned for the next release, or want to make sure your
idea fits where the project is headed? Check this repository's
[Milestones](../../milestones).

## Proposing a Feature

Before opening a `feature/**` branch, open an Issue describing the idea
first. This avoids spending effort on a branch that might not be the right
fit, and gives everyone visibility into what's being considered.

An Issue labeled **`accepted`** means it's been reviewed and is welcome as
a contribution — that's the signal to go ahead and open a branch for it.
Reference the Issue number in your PR (e.g. `Closes #42`) so it closes
automatically once merged.

If there's a rough idea of when it should ship, the Issue may also be
added to a [Milestone](../../milestones) — that's about scheduling, a
separate signal from acceptance itself.

## Branching Model

This project uses a four-branch model:

| Branch | Purpose |
|---|---|
| `main` | Production — reflects the latest published release. |
| `release/<version>` | The only branch type for planned change. Covers both new work (features, minor/major versions) and urgent fixes (patches) — the origin decides which: `next` for normal work, `main` for a production bug that can't wait. |
| `next` | A continuous integration pool. Work that has already been reviewed and merged (via `feature/**`, `fix/**`, `chore/**`, etc.) waits here until someone decides to include it in the next `release/*`. `next` is never "paused" while a release stabilizes — it keeps accepting merges the whole time; anything that lands after a release is cut is automatically material for the *next* cycle. |
| `sandbox/**` | Personal, unprotected space. Branch from anywhere (`main`, `next`, wherever) to prototype an idea before formalizing it as a real `feature/**` branch. No required PR, no CI. |

`main`, `release/**`, and `next` are protected: every change requires a
pull request and a signed commit. `main` and `release/**` additionally
require **2 approving reviews** (in practice, CodeRabbit's automatic
approval plus one human review); `next` requires **1** (a human review —
CodeRabbit isn't auto-triggered there, though it can still be invoked
manually with `@coderabbitai review`). `sandbox/**` has no protection at
all. Repository/organization admins may bypass this for exceptional cases,
but this should be rare.

### Why this shape

A classic gap in simpler branching models is that an in-progress release
and a production bugfix are developed in isolation and never tested
against each other before both reach `main` — so one can silently
overwrite the other. This model avoids that by making sure both directions
of drift are surfaced automatically (see **Keeping releases in sync**
below) rather than relying on someone remembering to check.

## Branch Naming

| Prefix | Use for |
|---|---|
| `feature/<short-description>` | New functionality |
| `fix/<short-description>` | Bug fixes (general, not an in-flight release) |
| `bugfix/<short-description>` | A fix cut from an in-flight `release/*` branch during its stabilization |
| `chore/<short-description>` | Tooling, CI, dependency, or maintenance changes |
| `docs/<short-description>` | Documentation-only changes |
| `release/<version>` | A release in preparation, e.g. `release/1.1.0` or `release/1.0.1` for a patch |
| `sandbox/<anything>` | Personal, unprotected prototyping |

## Commit Convention

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)
(`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`, etc.), in English.

Pull requests are merged via **Squash and merge only** — never
**Rebase and merge**. GitHub does not re-sign rebased commits, which breaks
this repository's signed-commit requirement; squash and regular merge
commits are signed automatically by GitHub. When squashing, set the commit
subject explicitly rather than accepting GitHub's auto-generated message
(which concatenates individual commit messages and rarely follows
Conventional Commits).

## Pull Request Process

1. **Every change goes through a pull request** — no direct pushes to
   `main`, `release/**`, or `next`, regardless of how small the change is.
2. Open your PR with a Conventional-Commits-style title. You can write it
   yourself, or include `@coderabbitai` in the title to have
   [CodeRabbit](https://coderabbit.ai) generate one automatically.
3. **CodeRabbit** reviews PRs targeting `main` and `release/**`
   automatically, and can approve a PR on its own when it finds no
   blocking issues — confirmed working in practice, satisfying this
   repository's required review count. PRs targeting `next` are not
   auto-reviewed by CodeRabbit, to conserve this project's shared
   open-source review quota for the branches closest to release; `next`
   is still protected by CI and human review.
4. Dependabot PRs are excluded from CodeRabbit review and from SonarQube
   analysis (there's no meaningful code to review in a version bump).
5. The **`build`** status check (compile + full test suite) must pass
   before merging. SonarQube Cloud analysis also runs on every PR but is
   currently informative-only, not a required check — it will be promoted
   once the quality gate has proven stable over time.
6. Concurrency control automatically cancels a PR's in-progress CI run
   when a new commit is pushed, so only the latest commit's result
   matters.
7. New automation touching protected branches (workflows, not application
   code) should go through this same PR process before merging, even when
   it has already been tested manually — a second review has consistently
   caught real issues (missing permissions, pagination limits, missing
   concurrency handling) that manual testing alone missed.

## Keeping releases in sync

Because `release/*` branches can be cut from either `next` (normal work)
or `main` (a patch), two kinds of drift are worth being aware of:

- **`main` moves ahead of an open `release/*`.** If another release or
  patch reaches `main` while a `release/*` is still being stabilized, that
  branch is now behind production. An automated check runs on every push
  to `main` and posts a reminder on any open `release/*` PR that has
  fallen behind or diverged, so this doesn't go unnoticed.
- **A `bugfix/**` is merged into a `release/*`.** A bug found and fixed
  while stabilizing a release needs to reach `next` too, or it only
  exists in that one release and can silently resurface in the next one.
  An automated reminder posts on the merged PR when this happens.

Both are reminders, not automatic merges — resolving them (forward-merging
`main` into a release, or propagating a bugfix to `next`) is handled by
whoever is already responsible for that release.

## Release Process

Cutting and publishing a release is handled by the project maintainer, or
by contributors who have been personally entrusted with that
responsibility.

In short: a `release/<version>` branch is cut from `next` (normal release)
or `main` (urgent patch), stabilized via its own PR into `main`, and once
merged, published by creating a GitHub Release (tag `v<version>`), which
triggers the test suite and the Maven Central publish pipeline. Releases
are marked **Pre-release** while the project is still in the `0.x` line.

## Local Development

See the [README](./README.md) for prerequisites (Java 21, Docker for
integration tests) and setup instructions.

## Code Style & Testing Expectations

See [AGENTS.md](./AGENTS.md) for this project's engineering discipline —
verifying behavior empirically rather than assuming it, English-only
comments, one change at a time, and the project's testing philosophy.

## Security

See [SECURITY.md](./SECURITY.md) for how to report a vulnerability.

## Issues

*Coming soon — issue templates are not yet defined for this project.*