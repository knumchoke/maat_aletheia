# Ma'at.Aletheia (MA)

**Integrity assurance for paper-based examinations.**

> Truth cannot be rewritten.

Ma'at.Aletheia is an open-source framework that makes scanned examination documents — OMR answer sheets, handwritten papers, and other examination artifacts — verifiable, auditable, and tamper-evident. It adds cryptographic evidence to existing examination workflows without replacing the scanners, OMR software, or databases already in place.

**Status: design stage.** The [project proposal](docs/project_proposal.md) (v0.6 draft, revised after an external trust-model review) is complete; Phase 1 implementation has not started. This is the right time to challenge the design — issues and critique are welcome.

---

## The problem

In most examination workflows, an answer sheet is scanned, processed, and scored — and at no point can anyone *prove* that:

- the scanned image was never modified after acquisition,
- the score or grade was computed from that exact image,
- no file was replaced, and no page or document quietly removed.

Once a scanned image is altered, distinguishing the original from the forgery may be impossible. For high-stakes examinations, that is an unacceptable gap.

MA does not try to prevent every attack. It enforces a stronger, simpler principle:

> **Every modification must be detectable.**

## How it works

Every scanned document produces an **evidence package**: the page images, a manifest committing to the ordered page hashes (anchored from Phase 1, signed from Phase 2), and an **external anchor** — a commitment to an append-only location (transparency log, RFC 3161 timestamp authority, or witnessed Merkle root) that the operating organization cannot rewrite. Anchor types are not interchangeable — each batch records its **anchor class**, and the deployment declares one anchor location up front so a conflicting "second anchor" is detectable, not merely unobserved.

```text
roster pre-commitment → scan → hash → manifest → external anchor → OMR / grading → anchored result record
```

Detection derives from the anchor, not from the operator's goodwill:

- **Content** — any modified image fails hash verification against the anchored manifest.
- **Existence** — the candidate roster and session schedule are anchored *before* the exam by a role separate from scanning, and every batch is bound to that pre-commitment — so a missing document, a removed page, or an entire batch that never shows up is as detectable as an altered image.
- **Results** — result records are anchored and committed to the exact images *and* processing inputs (answer key, template, configuration) they were computed from. Automated (OMR) results are reproducible by a verifier; human-graded results are attributed to an individually enrolled grader key.
- **History** — legitimate rescans and re-grades supersede, never overwrite, under dual authorization; processed images are registered as derivations of anchored originals; the audit log is hash-chained into the anchors. Nothing is ever modified in place.
- **Candidates** — every examinee gets a receipt at scan time (full manifest hash via QR) and a day-one way to verify it, making each candidate an independent witness.

Anyone holding the public verification material can check all of this independently — without trusting the operating organization's storage, and without learning any candidate's identity.

## What MA proves — and what it doesn't

MA is deliberately honest about its boundary. From the moment a document's batch is anchored, any modification, substitution, or deletion is detectable. What happens **before** anchoring — a doctored sheet, a substituted paper — is outside the cryptographic boundary and is addressed by procedure and by keeping the window small: controlled scanning stations, sealed batch handling, a declared (and verifier-checked) maximum scan-to-anchor latency, and candidate receipts that fix the document's hash in the examinee's hands at scan time.

MA explicitly does **not** replace OMR or grading software, perform image processing, or claim to eliminate insider fraud through technology alone. See the [Trust Model](docs/project_proposal.md#trust-model) for the full statement.

## Design principles

- **Integrity first** — evidence authenticity over operational convenience.
- **Zero trust** — no party, including the operator, is a trusted rewrite authority.
- **Privacy by design** — public artifacts carry pseudonymous commitments, never candidate identities; only hashes are ever published, never pixels.
- **Vendor independence** — MA operates alongside existing scanners, OMR software, and databases. No proprietary hardware or SDKs.
- **Build on standards** — RFC 3161 timestamps, Merkle commitments, Sigstore/Rekor transparency logs, and C2PA alignment instead of invented cryptography.

## Roadmap

| Phase | Focus |
|-------|-------|
| **1 — Minimum detectable tampering** | Page hashing, document manifests, roster pre-commitment and session registry, batch anchoring with first-anchor-wins uniqueness, minimal supersession, RFC 3161 timestamps, candidate receipts with day-one verification, hash-chained audit log, CLI verifier |
| **2 — Signatures and binding** | Digital signatures and key management, OMR result binding with committed processing inputs, result supersession (re-grades), derivation records, full supersession workflow |
| **3 — Integration** | Attested (human grading) binding profile with individual grader keys, Sigstore/Rekor, multi-party verification, APIs |
| **4 — Ecosystem** | C2PA interoperability, mobile/web capture clients, public verification portal |

External anchoring is in Phase 1 by design: hashing without an anchor the operator cannot rewrite would create the *appearance* of integrity without the substance — which, in a high-stakes context, is worse than nothing.

## Documentation

- [Project proposal](docs/project_proposal.md) — full architecture, trust model, and component design.

## Who this is for

Government examination agencies, universities, professional certification bodies, independent auditors, and digital forensic investigators — anyone who needs to answer "has this examination evidence been tampered with?" with cryptography instead of assurances.

## License

Code is licensed under [Apache-2.0](LICENSE). Security never depends on secrecy: the entire framework — design, code, and verification rules — is open, and anyone may implement the specification.

## Name

**Ma'at** — the ancient Egyptian goddess of truth, justice, order, and balance.
**Aletheia** — the ancient Greek concept of truth unconcealed.

Together: *justice through verifiable truth.*
