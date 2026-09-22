# Project collaboration rules

## Pull requests and Git workflow

- Treat the phrase "new PR" literally: never push new work to a branch that already has an open pull request.
- Before creating a PR, inspect the current branch, remotes, and existing PRs for that branch.
- Create a dedicated branch from `main` for the requested change, using the `codex/` prefix unless the user explicitly requests another name.
- Keep existing branches, commits, and pull requests unchanged. Never force-push, retitle, edit, or otherwise modify an existing PR unless the user explicitly asks for that exact change.
- If work was accidentally placed on an existing PR branch, stop and separate it before opening the new PR; do not silently repair the existing PR.
- Commit only files that belong to the requested change and verify the final diff before pushing.

## Pull request descriptions

- Write PR titles and descriptions in English unless the user requests another language.
- Use real Markdown formatting: headings, blank lines, numbered steps, bullet lists, and fenced or inline code where appropriate.
- For GitHub CLI, prefer `--body-file` with a UTF-8 Markdown file so newlines and formatting are preserved.
- After creating or editing a PR, verify the body through the GitHub API or `gh pr view` and confirm that the rendered content contains real newlines.
- Include a concise overview, user-visible behavior, implementation details, compatibility notes, validation commands, and relevant follow-up limitations.

## Validation and handoff

- Run the most relevant build or test command before reporting completion.
- If the change is installed on a device, state the device and installation result.
- Report the exact commit and PR URL, and clearly mention any known limitation.

## Local skills

- When a task clearly matches one or more skills installed under `.agents/skills`, inspect and use the relevant skill instructions proactively, even when the user does not explicitly name the skill.
- Choose only the skills that materially help with the task; do not load unrelated skills or force a skill into a discussion that does not need implementation guidance.
- Read the selected `SKILL.md` completely before taking task actions, and follow its workflow unless the user gives a conflicting instruction.
- Mention in the working update which skill is being used and why. In the final response, briefly note any material decision or change that resulted from using it.
- Prefer project-local skills over global or unrelated skills when both cover the same task.

## Resentin-specific engineering rules

- Inspect the actual Resentin implementation before proposing or implementing a feature. Distinguish what already exists from what is genuinely missing.
- Treat Grappa-IRC as the protocol boundary: prefer client-side UX improvements that preserve ordinary IRC-compatible text, and do not introduce Grappa API, WebSocket, or IRC protocol changes without an explicit design decision.
- Keep chat behavior compatible with existing drafts, slash commands, !addquote, multiline sending, reply styles, and message actions unless the user explicitly requests a behavior change.
- Reuse the existing ViewModel, StateFlow, preferences, repository, and Compose patterns instead of introducing parallel state or storage systems without a clear need.
- Keep user-visible text in Android string resources and preserve localization conventions.
- When the user asks to analyze or discuss without writing code, do not modify the repository.
- For composer or reply changes, validate at least: existing draft plus reply, cancellation, replacing the selected message, each configured reply style, send failure and retry, slash commands, and multiline messages.