# GitHub rulesets — do not touch

**Law 9:** Agents and automation must **never** create, update, disable, delete, or reconfigure repository or organization rulesets.

If a push or PR is blocked by a ruleset, stop and ask a human maintainer with repo admin access. Do not “fix” protection by mutating rules.

## Limiting `gh`

Prefer authenticating `gh` with a token that **cannot** manage rulesets:

- Fine-grained PAT: grant Contents / Pull requests / Workflows as needed; **omit** Administration.
- Classic PAT / OAuth: avoid `admin:org` and repository admin scopes used only for rules.

Do not run:

```bash
gh api …/rulesets          # any mutating method
gh api …/rules             # mutations
```

Read-only inspection of rules (GET) is discouraged for agents; ask a human if diagnosis is needed.
