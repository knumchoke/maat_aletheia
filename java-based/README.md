# Ma'at.Aletheia — capstone slice (Java 21 + Spring Boot)

The 3-day build of the vertical slice **hash → anchor → tamper → detect** from
[../docs/capstone_scope.md](../docs/capstone_scope.md), against the design in
[../docs/phase1_architecture.md](../docs/phase1_architecture.md).
Work plan: [SPRINT_PLAN.md](SPRINT_PLAN.md).

## Modules

| Module | What | Owner |
|---|---|---|
| `ma-core` | Schemas, JCS canonicalization, hashing conventions, roster commitments, batch building, anchor adapters, **the verdict engine** | Dev A (verify) + Dev B (anchor) |
| `ma-app` | Spring Boot operator console: registry / gateway / verify pages over `ma-core` | Dev B |
| `ma-verify` | Standalone shaded-jar verifier — what an independent party runs. Reads only `public/` + `evidence/`. | Dev A |

## Quickstart

```bash
mvn test                 # skeleton is green from day 0; @Disabled tests are the task list
mvn package
java -jar ma-app/target/ma-app-0.1.0-SNAPSHOT.jar     # → http://localhost:8080
# demo inputs: testdata/roster.csv + testdata/scans/*.png
# standalone verifier:
java -jar ma-verify/target/ma-verify-0.1.0-SNAPSHOT.jar batch workdir/public workdir/evidence EXAM-2026-DEMO-V1
```

Anchoring starts in `fake` mode (offline dev; the verifier reports it as
`UNANCHORED` — that is correct behaviour, not a bug). Flip
`ma.anchor.mode=rfc3161` in `ma-app/src/main/resources/application.properties`
once the TSA adapter lands (Dev B, day 1).

## The rules that are not negotiable

The eight invariants in [../docs/handoff.md](../docs/handoff.md) §4. The ones you
will collide with while coding:

1. **Append-only** — `Store` refuses overwrites by design. Don't route around it.
2. **Hash the final bytes** — record hashes are always over JCS bytes
   (`CanonicalJson.hash`), page hashes always over the exact uploaded bytes.
3. **Verdicts are multi-valued** — never boil a report down to a boolean, an
   exit code, or a green checkmark.
4. **Nothing published leaks candidate identity** — candidate ids exist only in
   the session pack (operator side) and in the candidate's own head.

## Definition of done

`VerifierContractTest` fully enabled and green, `Rfc3161AnchorLiveTest` passed
live at least once, and the demo script in SPRINT_PLAN.md runs end to end with
`ma.anchor.mode=rfc3161`.
