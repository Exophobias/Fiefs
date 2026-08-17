# Contributing

## Thank You

Thank you for your interest in contributing to Fiefs! This guide will help you get started.

## Links

- [Website](https://dansplugins.com)
- [Discord](https://discord.gg/xXtuAQ2)

## Requirements

- A GitHub account
- Git installed on your local machine
- A Java IDE or text editor
- A basic understanding of Java
- [Medieval Factions](https://github.com/Dans-Plugins/Medieval-Factions) (required dependency)

## Getting Started

1. [Sign up for GitHub](https://github.com/signup) if you don't have an account.
2. Fork the repository by clicking **Fork** at the top right of the repo page.
3. Clone your fork: `git clone https://github.com/<your-username>/Fiefs.git`
4. Open the project in your IDE.
5. Build the plugin: `mvn clean package`
   If you encounter errors, please open an issue.

## Identifying What to Work On

### Issues

Work items are tracked as [GitHub issues](https://github.com/Dans-Plugins/Fiefs/issues).

### Milestones

Issues are grouped into [milestones](https://github.com/Dans-Plugins/Fiefs/milestones) representing upcoming releases.

## Making Changes

1. Make sure an issue exists for the work. If not, create one.
2. Switch to `main`: `git checkout main`
3. Create a branch, using one of the prefixes below: `git checkout -b fix/kick-command-uuid-lookup`
4. Make your changes.
5. Test your changes.
6. Commit: `git commit -m "Fix the kick command's member lookup"`
7. Push: `git push origin <branch-name>`
8. Open a pull request against `main`, and write `Closes #<number>` in the description so the
   related issue is closed automatically when the pull request is merged.
9. Address review feedback.

## Commit and Pull Request Conventions

### Branch names

Prefix the branch with the kind of work it contains, then describe the work itself — use
`feature/add-export-command`, not `feature/issue-42`. The prefixes in use are:

- `feature/` — a new capability
- `fix/` — a correction
- `chore/` — maintenance, releases, and repository metadata
- `test/` — test-only work

### Commit messages

- Write the subject in the imperative mood: "Add the rename command", not "Added" or "Adds".
- Leave off the trailing period.
- Do not add a `Co-Authored-By` trailer unless an AI agent actually co-authored the commit.

Fiefs is developed AI-first, so many commits here are agent-authored. When an agent writes one, the
message is passed through a HEREDOC so the trailer lands on its own line rather than being mangled
by shell quoting, and the trailer names the model that actually did the work:

```bash
git commit -m "$(cat <<'EOF'
Add the whois command

Co-Authored-By: <the model that authored the commit> <noreply@anthropic.com>
EOF
)"
```

### Pull requests

- Pull requests are squash-merged. The history also contains merge commits — the most recent being
  the 0.12.0 release pull request — so squashing describes what new work should follow rather than
  every entry in the log.
- Close the issue from the pull request body with `Closes #<number>`. A bare `#<number>` links the
  issue but does not close it.
- Check each `Closes #<number>` against the issue it names before the pull request is opened, rather
  than carrying a number forward from an earlier planning step unverified.
- Keep the documentation in step with the change: `COMMANDS.md`, `USER_GUIDE.md`, `CONFIG.md`,
  `src/main/resources/plugin.yml` and `CHANGELOG.md` are the sources of truth for commands,
  permission nodes, configuration options and user-visible changes respectively.

These conventions follow
[dms-conventions](https://github.com/dmccoystephenson/dms-conventions/blob/main/docs/COMMIT_PR_CONVENTIONS.md).

## Testing

    mvn clean package

Place the Fiefs JAR and Medieval Factions JAR in your test server's `plugins/` folder.

## Questions

Ask in the [Discord server](https://discord.gg/xXtuAQ2).
