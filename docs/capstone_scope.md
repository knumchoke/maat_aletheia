# Ma'at.Aletheia — Capstone Scope

**Purpose:** the subset of [phase1_architecture.md](phase1_architecture.md) that a student group builds for a final "Professional Experience in Computer Science" assignment. The full architecture document remains the design artifact and future-work reference; this document is the build contract for the semester.

**Date:** 2026-07-05

---

## 1. Assumptions (adjust before kickoff)

- **Duration:** 12 working weeks. If your semester differs, stretch or compress §6 proportionally — cut from the tail (stretch goals), never from the core.
- **Team:** 4–5 members of uneven availability and skill. The plan assumes at least **two** members can be relied on for correctness-critical code.
- **No infrastructure budget.** Everything runs on laptops; the external anchor is a free public RFC 3161 TSA (e.g. freeTSA.org, DigiCert). No servers, no cloud.
- All eight invariants from [handoff.md](handoff.md) §4 still bind. Scope is cut by **removing components**, never by weakening an invariant that a shipped component touches.

## 2. The thesis (what the demo must show)

One vertical slice, end to end:

> **hash → anchor → tamper → detect**

Demo script (this is the acceptance test for the whole project):

1. `ma-registry commit roster.csv` — the "registration authority" anchors a roster commitment for a session, before "exam day".
2. `ma-gateway ingest ./scans --session S` — a folder of scanned images becomes evidence packages: normalized, hashed, manifested, Merkle-committed against the pre-anchored roster.
3. `ma-gateway anchor` — the batch record is timestamped by a public RFC 3161 TSA. Receipts (QR PNGs) are emitted per document.
4. **Tamper:** modify one page image; separately, delete one document; separately, swap in a substitute manifest.
5. `ma-verify batch ...` and `ma-verify receipt ...` — an independent binary, given only the public artifacts and a receipt, reports **`tampered`**, the omission, and the substitution — each with the correct multi-valued verdict, never a bare pass/fail.

If a feature does not make that script better, it is out of scope.

## 3. Scope

### 3.1 Build in full (the core)

| Item | From architecture doc | Notes |
|---|---|---|
| Canonical JSON + hash-string convention | §3.1 | RFC 8785 (JCS), `alg:hex` prefixed hashes. Foundation for everything; do first. |
| Document manifest | §3.2 `ma/manifest/v1` | Schema as designed, minus signature fields. |
| Batch record | §3.3 `ma/batch/v1` | As designed. |
| Session registry record | §3.4 `ma/session/v1` | As designed. |
| Candidate receipt | §3.5 `MA1` QR payload | Full 256-bit manifest hash + session ID + per-record salt. |
| Anchor proof + inclusion proof | §3.6, §3.7 | TSA variant only. |
| RFC 6962 Merkle tree | §5.2 | Domain-separated leaves, SHA-256, audit paths. Use an existing audited library where the language allows; write property tests regardless. |
| RFC 3161 TSA anchor adapter | §4.3 | One adapter behind the anchor interface; the interface itself stays (it is cheap and honest to the architecture). |
| **CLI verifier `ma-verify`** | §4.5 | **The product.** Full multi-valued verdicts: `current`, `tampered`, `missing-from-roster`, `unanchored`, `conflicting-batch` (first-anchor-wins check), plus `superseded` only if the stretch goal in §3.4 lands. Accepts a receipt directly. |

### 3.2 Build in trivial form

| Item | Trivial form | What is deferred |
|---|---|---|
| Session Registry (§4.1) | `ma-registry` = one CLI command: hash a roster CSV (salted commitments), build the roster tree, anchor the root via the same TSA adapter. RA-generated salts handed over as a JSON "session pack" file. | Any service form; venue scheduling beyond a `(session_id, window)` pair. |
| Scanner Gateway (§4.2) | `ma-gateway ingest <folder>` — explicit invocation over a folder of images. Normalization = "the deployment's canonical format is whatever the scanner emits; bytes are hashed as-is" (declare this in the report; Inv-3 is satisfied by *fixing* the format, not by converting). | Watch-folder daemon, TWAIN/SANE, dwell auditing, offline anchor spool. |
| Receipt issuance (§4.4) | Write a QR PNG + plaintext line per document. | Printer/display integration. |
| Storage layout (§5.3) | Content-addressed directory tree, two tiers only: `public/` (batch records, hash lists, proofs) and `evidence/` (manifests + images). | Four-tier access control, encryption at rest, dual-person export, object-store backend. |
| Published hash list (§3.9) | A JSON file in `public/`; "publication" = committing it to the project git repo. | Any real publication channel. |

### 3.3 Cut entirely (future work in the report, §8 of the report maps each to the architecture doc)

- Audit chain (§3.10, §4.6) and its fold into the batch anchor
- Deployment signing key (§5.4) — Phase 1 already treats it as optional; the anchor is the load-bearing control (Inv-4)
- Witnessed published-root fallback adapter (§4.3 second class)
- Supersession / rescan handling (see §3.4 stretch goal)
- Public verification material bundle (§3.8) — the verifier pins the TSA certificate and roster-anchor reference from a checked-in config file; state plainly in the report that PVM distribution (M-6) is unimplemented
- Detached anchoring / offline queueing (§5.6)
- Result binding, derivation records, anything Phase 2+

### 3.4 Stretch goals — in priority order, only after the §2 demo passes end to end

1. **Minimal supersession** (`supersedes` field + anchored superseding manifest + `superseded` / duplicate verdicts). Highest value: it completes the M-9 story and adds the most interesting verdict.
2. Second anchor adapter (witnessed root written to a git repo as the "hard-to-rewrite channel") — proves the adapter interface is real.
3. Reproducible-build recipe for `ma-verify` (pinned toolchain + `diff` of two independent builds in CI).

## 4. Deliverables

1. **Code:** three CLIs (`ma-registry`, `ma-gateway`, `ma-verify`) in one repository, with the golden-vector test suite (§5) green in CI.
2. **Demo:** the §2 script, live, plus a recorded run as backup.
3. **Report:** reuse the existing corpus — proposal v0.6, the security-review findings table (C-1…M-9), [phase1_architecture.md](phase1_architecture.md) including the threat analysis (§7). The report's structure: trust model → review → full architecture → *implemented slice* (this document) → evaluation against the invariants → future work (§3.3 cut list). This traceability chain is the report's differentiator; very few capstone projects have an externally reviewed threat model.

## 5. Golden test vectors — the integration contract (weeks 1–2, before any component code)

A frozen `testdata/` directory:

- 2 sample rosters (one with salts), 1 session registry record
- 6–8 sample page images; hand-computed manifests and batch record for them (computed once, reviewed by two members, then frozen)
- The full expected Merkle tree (leaves, root, every audit path) for the sample batch
- One real TSA response for the sample batch root, captured and checked in
- Tampered variants: modified page, dropped document, substituted manifest, second conflicting batch for the same session ID
- For each variant: the exact expected `ma-verify` output (verdict + machine-readable JSON)

Rules: vectors change only by whole-team agreement; every component's tests run against them; `ma-verify` against the vectors **is** the integration test. A component is "done" when it reproduces the vectors byte-for-byte, not when its author says so.

## 6. Milestones (12 weeks)

| Weeks | Milestone | Verify by |
|---|---|---|
| 1–2 | Repo, CI, language toolchain pinned; JCS canonicalization + hash convention library; **golden vectors frozen** | Canonicalization round-trips the vector manifests byte-for-byte |
| 3–4 | Merkle library + schemas (manifest, batch, session, proofs) | Vector tree root and audit paths reproduced |
| 5–6 | `ma-registry` + `ma-gateway ingest` (no anchoring yet) | Ingesting vector images reproduces the frozen manifests and batch record |
| 7 | TSA adapter + `ma-gateway anchor`; receipt PNGs | Checked-in TSA response validates; a live anchor against freeTSA succeeds |
| 8–9 | `ma-verify`: all verdicts, receipt path | Every tampered vector yields its expected verdict; **§2 demo script passes end to end** |
| 10 | Buffer. It will be consumed. | — |
| 11 | Stretch goal 1 if (and only if) week 10 was not fully consumed; otherwise polish + demo rehearsal | Recorded demo run exists |
| 12 | Report finalization, presentation | — |

The week-9 gate is the point of no return: if the demo script does not pass by end of week 9, cut stretch goals and receipts polish, not the verifier.

## 7. Team allocation (containing the uneven-quality risk)

Every component is a separate CLI over files with frozen schemas — a member's failure is contained to their CLI and can be stubbed with a script without sinking the demo.

| Role | Load-bearing? | Work |
|---|---|---|
| **Verifier owner** (strongest member) | Yes — correctness is the thesis | `ma-verify`, verdict logic, receipt path |
| **Crypto/format owner** (second strongest) | Yes | JCS canonicalization, Merkle, TSA adapter, schema library |
| Gateway owner | Contained | `ma-gateway ingest` + `anchor` orchestration, receipts (consumes the format library, no crypto authorship) |
| Registry + vectors owner | Contained | `ma-registry`, `testdata/` construction and maintenance, CI |
| Report owner / floater | Contained | Report assembly from the existing corpus, demo script, picks up slack |

With 4 members, merge the last two roles. The two load-bearing roles pair-review each other's merges; contained roles need one approving review from a load-bearing member.

**Dropout rule:** if a contained-role member disappears, their CLI is replaced by the simplest script that reproduces the golden vectors, and the report notes it. If a load-bearing member disappears, stretch goals are cancelled immediately and the remaining strong member absorbs the role — that is what week 10 exists for.

## 8. Explicitly out of scope — say so, don't hide it

The report must state (mirroring proposal *Security Goals* and architecture §7.3): no signatures (H-2 residual accepted, first-anchor-wins carries it), no audit chain (M-7 unimplemented), no PVM distribution (M-6 unimplemented), single anchor class, no offline operation, trivial storage access control. Each maps to a section of [phase1_architecture.md](phase1_architecture.md) showing it is *designed* — the capstone claim is "we built the load-bearing slice of a reviewed architecture," which is stronger and more honest than pretending the slice is the system.
