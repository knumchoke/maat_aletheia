# Handoff — Ma'at.Aletheia (MA) System Architecture

**Audience:** System architect taking the project from design (v0.6 proposal) to a Phase 1 architecture and implementation plan.

**Date:** 2026-07-05

---

## 1. Where the project stands

- **No code exists.** The repository contains design documents only.
- [project_proposal.md](project_proposal.md) is the authoritative document, at **v0.6 draft**.
- [README.md](../README.md) is the public summary and is aligned with v0.6.
- v0.5 underwent an external security review of its **trust model**. All Critical, High, and Medium findings were resolved in the v0.6 draft (see §3). Two Low findings remain open (see §6).

Your job is not to re-litigate the trust model — it has been reviewed and hardened. Your job is to turn it into a concrete Phase 1 architecture: components, schemas, interfaces, and deployment topology that preserve the invariants in §4.

## 2. What MA is, in one paragraph

MA makes scanned examination documents (OMR sheets, handwritten papers) tamper-evident. Every scanned document produces an evidence package (page images + manifest of ordered page hashes); batches of packages are committed to an **external anchor** the operating organization cannot rewrite; results are bound back to the anchored images and anchored themselves. The core threat is the **operating organization itself** — no single party, including the operator, is a trusted rewrite authority. Detection, not prevention: every modification, substitution, omission, or deletion after anchor time must be detectable by an independent verifier.

## 3. The security review, and what v0.6 changed

The v0.5 review found that the two headline guarantees — "omission as detectable as modification" and "non-repudiation via external anchoring" — rested on artifacts (the roster, the anchor) whose provenance stayed in the operator's hands. v0.6 resolved this. The changes below are **binding design requirements**, each with its proposal section:

| Finding | Resolution in v0.6 | Proposal section |
|---------|--------------------|------------------|
| C-1: roster provenance undefined | **Roster pre-commitment + session registry**: roster and session/venue schedule anchored *before* the exam by the registration authority (separate from scanning); batches reference the pre-anchored roster; verifier checks every pre-anchored session has an anchored batch | Trust Model → Completeness |
| H-1: anchor equivocation | **Anchor-class taxonomy** (monitored log > TSA > witnessed published root), anchor class recorded per batch, one pre-declared anchor location per deployment, **first-anchor-wins** per session identifier | Trust Model → Anchor strength and equivocation |
| H-2: Phase 1 unsigned substitution | First-anchor-wins uniqueness + optional single deployment signing key in Phase 1 | Evidence Package |
| H-3: unfaithful derivations | Derivation verifiability is a recorded property; reproducible derivations re-run by verifier; otherwise distinct verdict "bound via unverifiable derivation" | Artifact and Document Model |
| H-4: uncommitted processing inputs | Automated result records commit to answer key / template / config hashes; answer key anchored post-exam | MA Result Binder |
| M-1: scan-to-anchor window | Declared maximum scan-to-anchor latency, verifier-checked; guarantees begin at anchor time | Trust Model → What MA proves |
| M-2: result equivocation | Exactly one current result record per document; anchored result supersession for re-grades | MA Result Binder |
| M-3: receipt grinding / no verification path | ≥ 80-bit receipts (full-hash QR recommended), carries per-record salt; Phase 1 CLI + published batch hash lists | Candidate Receipts |
| M-4: weak supersession control | Authorizer signs superseding manifest with distinct key; anchored exception records; "superseded out-of-window" verdict | Handling Legitimate Rescans |
| M-5: privacy key custody | Per-session HMAC keys; salt delivered on receipt; pseudonymity scoped to external verifiers | Design Principles → Privacy by Design |
| M-6: trust-material distribution | **Public verification material** as a versioned bundle, published independently of the operator; reproducible verifier builds | Trust Model → Public verification material |
| M-7: unprotected audit log | Hash-chained audit log, chain head folded into every batch anchor | MA Audit |
| M-8: grader key provenance | Individually enrolled, revocable grader keys held outside the operator (Phase 3) | MA Result Binder → Attested |
| M-9: Phase 1 rescans undefined | Minimal supersession (`supersedes` field, anchored, defined duplicate verdict) pulled into Phase 1 | Roadmap → Phase 1 |

## 4. Non-negotiable invariants

Any architecture decision that violates one of these is wrong, regardless of how much it simplifies implementation:

1. **Never modify, always append.** Evidence packages, result records, and audit entries are never overwritten or deleted. Replacement happens only via anchored supersession.
2. **Anchor before processing.** A document's identity is externally fixed before OMR or grading touches it.
3. **Hash the final bytes.** Normalization strictly precedes hashing; nothing rewrites bytes after the hash is computed. Canonical image format is fixed per deployment before the first scan.
4. **The anchor is the load-bearing control, not the operator's key.** Signatures add attribution; detection derives from the external anchor.
5. **First anchor wins.** One batch record per pre-declared session identifier; conflicting records are verification failures.
6. **The verifier's verdicts are multi-valued.** "current", "superseded", "superseded out-of-window", "bound via unverifiable derivation", "result not reproducible by verifier", "tampered" — never collapse to pass/fail.
7. **Nothing published leaks candidate identity.** Only hashes leave the operator; candidate IDs appear only as per-session-keyed HMAC or per-record-salted commitments.
8. **Roster and schedule are anchored before the session, by a role separate from scanning.**

## 5. Architecture work needed (Phase 1 scope)

The Roadmap → Phase 1 section is the scope contract. Components to design:

### 5.1 Components

- **Session Registry / Roster Pre-commitment service** — used by the registration authority *before* exam day. Produces the anchored (roster commitment, session identifier, venue, declared window) records. Decide: standalone CLI vs. service; how the registration authority (organizationally separate from the operator) runs it.
- **Scanner Gateway** — watches/receives scanner output; normalization, page hashing, manifest construction, batch building against the pre-anchored roster, receipt issuance, anchor submission. Decide the ingestion interface with scanners (the proposal deliberately does not mandate one).
- **Anchor adapters** — a common interface over the three anchor classes: RFC 3161 TSA (Phase 1 requirement), published Merkle root with witnesses (Phase 1 fallback), Sigstore/Rekor (Phase 3, but the abstraction must anticipate it). Anchor class is recorded per batch.
- **Receipt issuance** — QR (full 256-bit manifest hash + batch identifier + per-record salt) with printer/display integration.
- **CLI Verifier** — the full check list in *MA Verifier*; accepts a candidate receipt directly; reproducible builds. This is the component third parties run — treat it as the product.
- **Audit chain** — hash-chained log with chain head folded into every batch anchor.

### 5.2 Schemas and formats (design these first)

- **Document manifest** — metadata fields per *MA Manifest*; must carry **algorithm agility** from day one (hash algorithm identifiers, versioned schema) — see open finding L-1.
- **Batch record** — session identifier, roster commitment reference, package set commitment (Merkle tree), anchor class, scan/anchor timestamps, audit chain head.
- **Session registry record** — roster commitment, session/venue identifiers, declared anchoring window.
- **Receipt format** — QR payload layout.
- **Public verification material bundle** — versioned: anchor class + endpoints, TSA/log public keys, key fingerprints, roster/session commitments, declared max scan-to-anchor latency. Decide the publication channel and update/rotation semantics.
- **Supersession record (minimal Phase 1 form)** — `supersedes` hash reference + reason + operator identity, anchored.

### 5.3 Key open decisions for the architect

1. Scanner ingestion interface (watch folder? TWAIN/SANE integration? both?) — the README deliberately no longer promises "the filesystem is the integration point"; whatever you choose must be analyzed against the scan-to-anchor window (M-1).
2. Merkle tree construction and proof format for batch commitments (consider RFC 6962-style trees for later Rekor compatibility).
3. Storage layout for evidence packages (filesystem vs. object store; access control is a first-class concern for handwritten scripts — see Privacy by Design).
4. How the optional Phase 1 deployment signing key is provisioned and where it lives.
5. Language/runtime. Constraints: CLI-first, reproducible builds required, long-term maintenance by an open-source community.
6. Offline operation: exam venues may lack connectivity — how batches queue for anchoring within the declared latency window.

## 6. Known open items (not blockers, but design for them)

- **L-1 (open):** Long-term validity — TSA certificate expiry, re-anchoring of batch roots, hash algorithm succession. Not solved in v0.6. *Minimum requirement now:* algorithm agility in every schema, and a format that permits later re-anchoring records.
- **L-2 (closed incidentally):** "Batch" is now defined as one anchored commitment per (session, venue) under a pre-declared session identifier.
- The proposal has no formal threat-analysis section (adversary enumeration / attack tree). The security review recommended adding one; it would be a natural companion to the architecture document you produce.

## 7. Reading order

1. [project_proposal.md](project_proposal.md) — in full; Trust Model and Roadmap → Phase 1 most carefully.
2. [README.md](../README.md) — the public claims your architecture must actually deliver.
3. Prior art to study before designing the anchor adapters: RFC 3161, RFC 6962 / Sigstore Rekor, C2PA manifest model (Phase 4 interop target — don't paint the manifest schema into a corner).
