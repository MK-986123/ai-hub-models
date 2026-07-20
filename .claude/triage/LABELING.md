# Grading Breeze Triage Accuracy

The nightly Breeze agent posts a soft-triage comment on every `[QAIHM Nightly]`
issue. The scorecard Breeze agent posts a regression-analysis comment on every
scorecard issue. The weekly KB-update agent
(`.claude/skills/kb-weekly-update/SKILL.md`) later reads those issues to score
how often each agent was right.

Without ground truth from humans, the weekly agent is effectively self-grading
and will over-claim accuracy. **Three labels in `qcom-ai-hub/tetracode` let you
give the weekly agent authoritative ground truth in 5 seconds.** The same three
labels work for both the nightly and scorecard issues.

## The three labels

| Label | When to apply (nightly issue) | When to apply (scorecard issue) |
|-------|-------------------------------|---------------------------------|
| `triage-correct` | Agent's named team AND root cause were both right. The fix landed where the agent said it would. | Agent's SUSTAINED / NEW / FLAKY classification + team routing held up: the regressions it called sustained actually persisted, and the team it pointed at owned the fix. |
| `triage-wrong` | Agent's team OR root cause was wrong (one being right is not enough — both must be). | Agent mis-clustered regressions, mis-classified SUSTAINED vs FLAKY, or pointed at the wrong team. One material error is enough. |
| `triage-transient` | Failure was a flake / retry-passed / runner glitch. No real RCA was needed. | The whole regression batch turned out to be infra noise (single-run shock, baseline reset). No real RCA was needed. |

If you cannot pick one cleanly, **leave the issue unlabeled**. The weekly agent
will mark it `UNVERIFIED` — that is a correct outcome, not a missing signal.

## When to apply

At close time, when you already know what actually fixed (or didn't fix) the
issue. One click in the GitHub Labels dropdown, then close. That's the whole
workflow.

If you discover later that an earlier verdict was wrong, change the label —
the next weekly run will pick up the new truth.

## What if the triage was *mostly* right?

If routing was right but the RCA was wrong (or vice versa): apply `triage-wrong`
and drop a one-line comment with the real RCA. The weekly agent reads
comments — your sentence becomes evidence cited in next week's report.

## How the weekly agent uses these labels

In `.claude/skills/kb-weekly-update/SKILL.md`, labels are the **highest-priority
signal**. They override fix-PR matching, comment scanning, timeline heuristics,
and (for scorecard) the SUSTAINED-reappearance math.

For **nightly** issues (Step 2a):
- `triage-correct` → `VERIFIED-CORRECT`
- `triage-wrong` → `VERIFIED-INCORRECT`
- `triage-transient` → `TRANSIENT`

For **scorecard** issues (Step 2b):
- `triage-correct` → `SUSTAINED-CORRECT`
- `triage-wrong` → `SUSTAINED-WRONG`
- `triage-transient` → `NO-DATA`

The label is cited verbatim in the Evidence column of the weekly accuracy report.

## Bulk-grading historical issues

If you want to backfill a few weeks of accuracy data:

```
# Nightly issues
gh issue list --repo qcom-ai-hub/tetracode \
  --search "[QAIHM Nightly] is:closed closed:>=2026-06-01" \
  --label "ai-hub-models" --state closed --limit 30 \
  --json number,title,closedAt

# Scorecard issues
gh issue list --repo qcom-ai-hub/tetracode \
  --search "label:scorecard is:closed closed:>=2026-06-01" \
  --state closed --limit 30 \
  --json number,title,closedAt
```

Then walk each list and apply one of the three labels per issue. 30 issues × 5
seconds = under 3 minutes for a month of ground truth.

## Why not auto-label?

The whole point is independent verification. If the agent labels its own work,
we're back to self-grading. Labels must come from humans (or from a separately-
authored verifier with no shared context with the nightly agent) — otherwise the
weekly accuracy report is just the agent agreeing with itself.
