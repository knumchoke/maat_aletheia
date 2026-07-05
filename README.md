# Ma'at.Aletheia (MA)

**Integrity assurance for paper-based examinations.**

> Truth cannot be rewritten.

Ma'at.Aletheia is an open-source framework that makes scanned examination documents — OMR answer sheets, handwritten papers, and other examination artifacts — verifiable, auditable, and tamper-evident. It adds cryptographic evidence to existing examination workflows without replacing the scanners, OMR software, or databases already in place.

**Status: design stage.** The [project proposal](docs/project_proposal.md) (v0.5 draft) is complete; Phase 1 implementation has not started. This is the right time to challenge the design — issues and critique are welcome.

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

Every scanned document produces an **evidence package**: the page images, a signed manifest committing to the ordered page hashes, and an **external anchor** — a commitment to an append-only location (RFC 3161 timestamp authority, transparency log, or published Merkle root) that the operating organization cannot rewrite.

```text
scan → hash → manifest → external anchor → OMR / grading → anchored result record
```

Detection derives from the anchor, not from the operator's goodwill:

- **Content** — any modified image fails hash verification against the anchored manifest.
- **Existence** — batches are bound to the candidate roster and to declared page counts, so a missing document or removed page is as detectable as an altered one.
- **Results** — scores are bound back to the exact images they were computed from, and result records are anchored too.
- **History** — legitimate rescans supersede, never overwrite; processed images are registered as derivations of anchored originals. Nothing is ever modified in place.

Anyone holding the public verification material can check all of this independently — without trusting the operating organization's storage, and without learning any candidate's identity.

## What MA proves — and what it doesn't

MA is deliberately honest about its boundary. From the moment a document is registered and anchored, any modification, substitution, or deletion is detectable. What happens **before** registration — a doctored sheet, a substituted paper — is outside the cryptographic boundary and is addressed by procedure: controlled scanning stations, sealed batch handling, immediate anchoring, and candidate receipts that let every examinee verify their own document later.

MA explicitly does **not** replace OMR or grading software, perform image processing, or claim to eliminate insider fraud through technology alone. See the [Trust Model](docs/project_proposal.md#trust-model) for the full statement.

## Design principles

- **Integrity first** — evidence authenticity over operational convenience.
- **Zero trust** — no party, including the operator, is a trusted rewrite authority.
- **Privacy by design** — public artifacts carry pseudonymous commitments, never candidate identities; only hashes are ever published, never pixels.
- **Vendor independence** — the integration point is the filesystem: scanners write files, MA registers them. No proprietary hardware or SDKs.
- **Build on standards** — RFC 3161 timestamps, Merkle commitments, Sigstore/Rekor transparency logs, and C2PA alignment instead of invented cryptography.

## Roadmap

| Phase | Focus |
|-------|-------|
| **1 — Minimum detectable tampering** | Page hashing, document manifests, roster-bound Merkle batch commitments, RFC 3161 timestamps, candidate receipts, CLI verifier |
| **2 — Signatures and binding** | Digital signatures and key management, OMR result binding, derivation records, supersession workflow |
| **3 — Integration** | Attested (human grading) binding profile, Sigstore/Rekor, multi-party verification, APIs |
| **4 — Ecosystem** | C2PA interoperability, mobile/web capture clients, public verification portal |

External anchoring is in Phase 1 by design: hashing without an anchor the operator cannot rewrite would create the *appearance* of integrity without the substance — which, in a high-stakes context, is worse than nothing.

## Documentation

- [Project proposal](docs/project_proposal.md) — full architecture, trust model, threat analysis, and component design.

## Who this is for

Government examination agencies, universities, professional certification bodies, independent auditors, and digital forensic investigators — anyone who needs to answer "has this examination evidence been tampered with?" with cryptography instead of assurances.

## License

Code is licensed under [Apache-2.0](LICENSE). Security never depends on secrecy: the entire framework — design, code, and verification rules — is open, and anyone may implement the specification.

## Name

**Ma'at** — the ancient Egyptian goddess of truth, justice, order, and balance.
**Aletheia** — the ancient Greek concept of truth unconcealed.

Together: *justice through verifiable truth.*
