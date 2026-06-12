# Documentation

This folder is the single home for project documentation.

## Folders

- `ideas/` — early product ideas and discovery notes.
- `plans/` — implementation plans, architecture notes, and source-of-truth task breakdowns.
- `spec/` — product and technical specifications.
- `todos/` — shorter execution checklists derived from plans.
- `screenshots/` — UI references, mockups, and screen captures.

## Top-level Documents

- [`spec/SPEC.md`](spec/SPEC.md) — product and technical specification.

## Technical Plan PR Monitor

Use the local monitor script to watch GitHub pull requests for new technical plans:

```bash
./scripts/monitor-tech-plan-prs.sh
```

The script polls open PRs every 10 minutes, detects plan PRs from configured title
keywords, labels, or body headings, and records processed PR numbers in
`.codex/processed-tech-plan-prs.json`. That local state directory is ignored by
Git.

Configuration is via environment variables:

- `POLL_INTERVAL_SECONDS` — polling interval, default `600`.
- `GH_REPO` — repository in `owner/repo` form, default inferred from `origin`.
- `STATE_FILE` — processed PR state file, default `.codex/processed-tech-plan-prs.json`.
- `RUN_ONCE=1` — perform one poll and exit, useful for manual checks.

When the script detects a new, non-draft technical plan PR, it prints the PR
number, title, author, labels, URL, changed files, and body, records it as
processed, then exits so implementation can proceed on a separate working branch.
Draft PRs are skipped unless labeled `ready-for-implementation`.
