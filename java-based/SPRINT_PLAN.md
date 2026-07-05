# 3-Day Sprint Plan — Ma'at.Aletheia capstone slice

Supersedes the 12-week milestones in [../docs/capstone_scope.md](../docs/capstone_scope.md) §6;
everything else in that document (scope tables, invariants, demo script, report framing) still binds.

**Team:** Dev A (verifier owner — the product) and Dev B (crypto/anchor owner). Everyone else:
report assembly from the existing docs corpus + demo recording (no code dependencies on them).

**Ground rules**
- The contract tests in `ma-core/src/test/.../VerifierContractTest.java` are the spec. Never weaken an assertion.
- `@Disabled` tests are the task list: starting a task = removing its `@Disabled`.
- The store is append-only; if you feel the urge to overwrite a file, you are about to violate invariant 1 — stop.
- If a decision isn't covered here, `docs/phase1_architecture.md` wins.

## Day 1 — the risky bits first

| Slot | Dev A | Dev B |
|---|---|---|
| AM | Repo builds locally; run `mvn test` (skeleton is green). Read `Verifier` end to end. Start receipt path (`Verifier.verifyReceipt`, enable `receiptPath_…` test). | **The spike:** `Rfc3161Anchor.anchor()` against freeTSA (sketch in the class javadoc). Enable `Rfc3161AnchorLiveTest.anchorRoundTrip`. **Gate: live TSA round-trip by lunch** — if blocked > 2h, try DigiCert's TSA; if still blocked, escalate: we demo on witnessed-hash-in-git fallback instead. |
| PM | Finish receipt path incl. forged-receipt case. | `Rfc3161Anchor.verify()` (cert pinning; enable second live test). Capture one real token into `testdata/` + write an offline verify test around it. |

**End of day 1:** all `@Disabled` removed in ma-core, `mvn test` green with the live-TSA tests passing at least once locally.

## Day 2 — wire the web app, integrate

| Slot | Dev A | Dev B |
|---|---|---|
| AM | Remove the `checkAnchor` TODO carve-out in `Verifier` (anchor failures now = UNANCHORED, no excuses). Wire pinned TSA cert into `ma-verify/Main` (file path arg or bundled resource — pick, document as the M-6 stand-in). | `/gateway/anchor` endpoint (design note in the method javadoc: the unanchored→anchored transition vs append-only store — agree the mechanism with Dev A first, 15 min). Flip `ma.anchor.mode=rfc3161`. |
| **Midday** | **INTEGRATION GATE:** full demo script §2 of capstone_scope through the web UI with real TSA anchoring, on one machine. If this slips, cut receipt polish and tamper case 3 from the demo — never the verifier. | |
| PM | Run `ma-verify` (shaded jar) against a **copied** `public/` + `evidence/` on the other laptop — verdicts must match the web UI. | Tamper walk-through: page bytes (`testdata/tampered-cand-001.png`), manifest substitution, conflict file. Fix whatever surprises. |

**End of day 2:** demo script passes end to end, web + standalone CLI agree.

## Day 3 — harden, rehearse, record

- AM: edge cases found on day 2; UI verdict rendering readable for an audience; `README` quickstart verified from a clean clone on a machine that never built the project.
- PM: **record the full demo run** (backup for the live demo), rehearse live once with a timer, freeze the branch. Anything still broken becomes a sentence in the report, not a midnight commit.

## Demo-day script (10 min)

1. Registry page: upload `testdata/roster.csv` → show `public/session-….json` with its RFC 3161 anchor. "The roster is now fixed by a third party, before the exam."
2. Gateway: upload the 3 scans → receipts appear; anchor the batch.
3. Tamper live, three ways: `cp testdata/tampered-cand-001.png workdir/evidence/pages/<hash>.png`; edit one manifest field; drop a `batch-….conflict-1.json`.
4. Verify page → TAMPERED / TAMPERED / CONFLICTING_BATCH, with details. Then delete a manifest+rebuild-batch variant → MISSING_FROM_ROSTER ("omission is as detectable as modification").
5. Close on the standalone jar on the second laptop: same verdicts from only `public/` + `evidence/` + a receipt. "You don't have to trust us — that's the point."
