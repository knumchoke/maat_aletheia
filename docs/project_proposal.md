# Project Ma'at.Aletheia (MA)

**Integrity Assurance Framework for Paper-Based Examination Systems**

**Version:** 0.6 Draft

---

## Abstract

Project **Ma'at.Aletheia (MA)** is an open-source integrity assurance framework designed to protect paper-based examination workflows against unauthorized modification of scanned examination artifacts — OMR answer sheets, handwritten papers, and other scanned documents — and examination records.

The project addresses a fundamental security problem that exists in many examination systems:

> Digital copies of physical examination papers can be modified after scanning, while existing workflows often lack cryptographic mechanisms to prove whether the files remain authentic.

Instead of attempting to prevent every possible attack, MA follows a stronger security principle:

> **Every modification must be detectable.**

The framework introduces cryptographic evidence, externally anchored audit trails, and chain-of-custody verification into traditional examination workflows without requiring major changes to existing processing systems, whether Optical Mark Recognition (OMR) software or human grading workflows.

---

## Vision

To establish cryptographic trust for paper-based examinations by making every scanned examination document verifiable, auditable, and tamper-evident.

---

## Mission

Provide an open, transparent, and vendor-neutral framework that enables educational institutions, government agencies, and examination providers to verify the integrity of examination evidence throughout its entire lifecycle.

---

## Problem Statement

Current examination workflows typically consist of the following stages:

```text
Candidate
    |
    v
Examination Artifact
(answer sheet, written paper)
    |
    v
Scanner
    |
    v
Image File(s) (PDF/JPG)
    |
    v
Processing
(OMR or human grading)
    |
    v
Score Database
```

While this workflow successfully produces examination scores, it frequently lacks mechanisms for proving:

- that the scanned image has never been modified since acquisition,
- that the score or grade originated from the same image,
- that no unauthorized replacement occurred after scanning,
- that no page of a multi-page document was quietly removed.

Consequently, once a scanned image is altered, distinguishing between the original and modified version may become extremely difficult.

The absence of cryptographic integrity creates unnecessary risk in high-stakes examinations.

---

## Objectives

Project MA aims to introduce verifiable integrity without replacing existing examination systems.

Core objectives include:

- Image integrity verification
- Cryptographic evidence generation
- Externally anchored, append-only audit trail
- Chain of custody recording
- Result-to-image binding (automated OMR or attested human grading)
- Tamper detection
- Independent verification by third parties

---

## Trust Model

MA's guarantees have a precise starting point, and stating it honestly is a design requirement, not a weakness.

### What MA proves

MA's guarantees begin at **anchor time**. From the moment a batch is anchored, any subsequent modification, substitution, or deletion of an anchored image is detectable by anyone holding the public verification material.

Registration and anchoring are not the same moment, and the distinction matters. Between hashing and anchor publication the operator holds unanchored evidence that nothing cryptographic yet constrains. This **registration-to-anchor window** is therefore a controlled quantity, not an implementation detail: each deployment declares a maximum scan-to-anchor latency in its public verification material, every batch record carries both its scan and anchor timestamps, and the verifier reports any batch exceeding the declared latency. Candidate receipts (see *Candidate Receipts*) are the one control that reaches back to scan time itself: the receipt hash is fixed in the candidate's hands before the window opens.

### Completeness: existence, not only content

Content integrity alone is insufficient. The cleanest attack against an anchored system is not to alter an image but to ensure an image never enters the system at all. MA therefore binds every batch to a candidate roster: the anchored batch record commits to the session roster and to the exact set of evidence packages — one current package per roster entry. A verifier reports "candidate on roster, no evidence package" as a first-class failure, on par with a hash mismatch. Omission is thereby made as detectable as modification.

Omission detection, however, is only as strong as the roster's own provenance. If the organization that scans also assembles the roster at batch time, a candidate can be dropped from the roster and the package set together, and the batch verifies perfectly. MA therefore requires **roster pre-commitment**: the session roster (as pseudonymous commitments) and the session/venue schedule are anchored **before the examination session begins**, by the registration authority — a role or system organizationally separate from scanning operations — and every batch record references that pre-anchored roster commitment. The verifier checks the pair in both directions: every roster entry has a package, and every pre-anchored session in the **session registry** has an anchored batch within its declared window. Whole-batch suppression — a venue's batch that is simply never anchored — is thereby a first-class failure rather than a silent absence.

A **batch** is defined operationally as one anchored commitment per (session, venue) pair, under a session identifier pre-declared in the session registry. That identifier is what gives anchors something to be unique against (see *Anchor strength and equivocation*).

### What MA cannot prove

Cryptography cannot prove that a scanned image corresponds to the original physical document, or that the physical sheet itself was not altered before scanning. Events that occur **before** the point of registration are outside the cryptographic boundary and can only be addressed by procedure:

- dual-operator scanning stations,
- sealed batch handling with documented transfer,
- venue-level batch commitments published immediately after scanning,
- CCTV or equivalent monitoring of the scanning room.

MA treats these procedural controls as first-class deployment guidance, because the value of the cryptographic chain depends on how small the pre-registration window is kept.

### Capture clients (web and mobile upload)

Scanning stations are not the only capture path: a deployment may accept uploads from web or mobile clients. The cryptographic chain works identically — but the pre-registration window changes character entirely. A controlled scanning station keeps that window minutes wide under dual-operator supervision; a phone in an uncontrolled party's hands allows a doctored sheet to be photographed, or an image edited before upload, and MA will faithfully anchor the result. Deployments accepting client capture must shrink the window technically:

- capture happens in-app — never through a gallery or file picker,
- the image is hashed on-device at the moment of capture,
- the hash is submitted for anchoring immediately (seconds, not hours),
- the client is backed by platform device attestation (Play Integrity, App Attest),
- C2PA-capable capture, where available, signs the image at the sensor.

Client capture is a supported path, not a discouraged one — but every manifest records its **capture class** (scanning station or client capture), so a verifier can always distinguish the two and weigh the evidence accordingly.

### Key custody

A signature made with a key held by the same organization that operates the scanners proves nothing **against that organization** — it could alter an image, re-sign it, and rewrite the manifest. Non-repudiation therefore cannot rest on the operator's signature alone. MA addresses this in two ways:

1. **External anchoring is mandatory, not optional.** Every batch of evidence is committed to an external, append-only location that the operating organization cannot rewrite. Detection of tampering derives from the anchor, not from the operator's key. Anchor types are **not** interchangeable — see *Anchor strength and equivocation* below.
2. **Separation of duties for keys.** Recommended deployments place signing keys in hardware (HSM or smartcard) under a role that is organizationally separate from scanning operations and score processing, with documented key ceremonies. Where a truly independent key holder is unavailable, the external anchor remains the load-bearing control.

### Anchor strength and equivocation

An anchor the operator cannot rewrite can still be **equivocated**: the operator creates two conflicting anchored records — one genuine, one doctored — and later presents whichever suits them; each verifies perfectly in isolation, and no verifier ever sees both. The three permitted anchor classes differ materially in their resistance to this, so every batch record carries its **anchor class** — like capture class, a property the verifier weighs rather than hides:

1. **Monitored transparency log** (Sigstore/Rekor with independent witnesses) — resists both rewrite and equivocation. The strongest class and the recommended target.
2. **RFC 3161 timestamp authority** — proves that a hash existed at a point in time, and nothing more. It does not prove uniqueness: nothing stops an operator from timestamping two conflicting batch records in parallel.
3. **Self-published Merkle root** — the weakest class. A root on the operator's own website can be served differently to different parties (a split view) or quietly rotated. It is acceptable only with independent archival witnesses: publication through multiple third-party archivers, or a channel with issue-numbered permanence such as an official gazette or newspaper.

Two rules blunt equivocation regardless of class:

- **One pre-declared anchor location per deployment.** The anchor endpoint(s) are fixed in the public verification material before the first scan, so a "second anchor" is detectable in principle rather than merely unobserved.
- **First anchor wins.** Exactly one batch record is valid per pre-declared session identifier: the first one anchored. A later conflicting batch record for the same session identifier is a first-class verification failure, not an alternative version of events.

Candidate receipts reinforce both rules: every receipt in circulation is an independent, distributed witness — a substituted batch must survive comparison against every receipt issued from the genuine one.

### Public verification material

Independent verification is only as independent as the channel that delivers the verifier and its trust inputs. If the operator distributes the verifier, the anchor endpoints, and the key fingerprints, a compromised operator can ship a bundle that lies. MA therefore defines the **public verification material** as a concrete, versioned artifact containing:

- the deployment's declared anchor class and pre-declared anchor endpoint(s),
- transparency log and TSA public keys,
- deployment, authorizer, and grader signing-key fingerprints (as each phase introduces them),
- the pre-anchored roster and session-registry commitments,
- the declared maximum scan-to-anchor latency.

This bundle is published through a channel independent of the operating organization — the transparency log itself, the supervising examination authority or regulator, or the project's public infrastructure. The verifier is open source and distributed with reproducible builds, so anyone can confirm a binary matches the audited code.

---

## Design Principles

### Integrity First

The authenticity of examination evidence is more important than operational convenience.

### Zero Trust

No file is trusted solely because it exists inside an organization's infrastructure. Every artifact must be cryptographically verified, and no single party — including the operating organization — is a trusted rewrite authority.

### Evidence Preservation

Digital evidence should remain admissible, explainable, and independently verifiable.

### Privacy by Design

Examination evidence identifies real people. Public verification artifacts must not expose candidate identity — and because candidate identifier spaces are small and enumerable, a hash with a shared or published salt can be brute-forced in seconds and provides no real protection. Manifests published or shared beyond the operating organization therefore carry a pseudonymous commitment to the candidate identifier: either a hash with a high-entropy per-record salt disclosed only to the candidate (allowing them to verify their own record), or an HMAC under a key held separately from scanning operations. Verification of "this image is unmodified" must be possible without learning "whose exam this is."

These commitments are published to append-only locations and can never be recalled, so the commitment scheme's key discipline carries the privacy guarantee — permanently. MA therefore constrains it further:

- **HMAC keys are per-session (or per-batch), never long-lived.** Candidate identifier spaces are small and enumerable, so a single leaked long-lived key would retroactively deanonymize every record ever published. A per-session key bounds the blast radius to the sessions it covers. Key custody, rotation, and a documented compromise response (which sessions are affected, who is notified) are mandatory deployment guidance.
- **Per-record salts are delivered to the candidate at scan time** — printed on the candidate receipt is the natural vehicle. A salt held only by the operator turns the candidate's own verification into a favor the operator can withhold at dispute time.
- **Pseudonymity holds against external verifiers, not against the operator.** The operating organization necessarily knows the mapping from candidate to record; the claim is that published artifacts do not leak it.
- **Public-log permanence is a data-protection commitment.** Erasure of anchored commitments is impossible by design. A deployment must be able to justify, under its applicable data-protection law, that a properly keyed commitment is not re-identifiable — which is exactly why the per-session key discipline above is mandatory rather than recommended.

Written papers raise the stakes further: handwriting is effectively a biometric, and free-text content may identify the candidate directly no matter how well the manifest is pseudonymized. Only hashes are ever published — never pixels — and access control on the stored images is a first-class deployment concern, not an afterthought.

### Vendor Independence

The framework should operate alongside existing scanners, OMR software, and databases without requiring proprietary hardware.

### Build on Standards

MA reuses existing, audited mechanisms instead of inventing new ones. See *Prior Art and Standards* below.

---

## Proposed Architecture

```text
   Physical Examination Artifact
                 |
                 v
        High Resolution Scan
                 |
                 v
        MA Evidence Generator
                 |
    +------------+------------+
    |            |            |
    v            v            v
SHA-256      Digital      Trusted
 Hash       Signature    Timestamp
    |            |            |
    +------------+------------+
                 |
                 v
     Evidence Manifest (.json)
                 |
                 v
   External Anchor (transparency
   log / TSA / published root)
                 |
                 v
    Processing (OMR software
       or human grading)
                 |
                 v
    Result Record (signed,
   references page hashes,
   anchored in next batch)
                 |
                 v
       Examination Result
```

Three properties of this pipeline are essential:

1. **Anchoring happens before processing.** The document's identity is fixed externally before any downstream processing can occur.
2. **The result is bound back to the image.** After processing, a second signed record is produced containing the result hash **and** the page hashes it was derived from. This closes the loop: a verifier can confirm that a given score or grade derives from a given document, and that the document is the one anchored at scan time. Where processing went through a derived image, the strength of this loop depends on derivation verifiability — see *Artifact and Document Model*.
3. **Result records are anchored, not merely signed.** A signed-but-unanchored score record would inherit the key-custody problem described in the Trust Model: the operating organization could silently recompute and re-sign it. Result records therefore ride the same batch anchoring mechanism as manifests. MA's cryptographic guarantee ends at the anchored result record; deployments should verify published results against these records as part of the publication process.

---

## Artifact and Document Model

An examination artifact is an **ordered set of one or more page images**. A single-sheet OMR form is simply the one-page case; a handwritten script is the general case. The same evidence chain covers both.

Multi-page documents introduce a new omission attack: a page quietly removed must be as detectable as a page altered. The document manifest therefore commits to the ordered list of page hashes and a declared page count, fixed at scan time. Completeness exists at three levels, and the verifier checks all three:

1. **Roster level** — every roster entry has exactly one current document.
2. **Document level** — every declared page is present.
3. **Page level** — every page matches its anchored hash, in the declared order.

Supersession operates on whole documents, never on individual pages: a rescan of any page produces a new document manifest that supersedes the old one. Page-level supersession would make the audit history unmanageable.

Captured images often cannot be processed as-is — a phone photograph must be dewarped and recompressed before OMR software can read it — yet processing must never touch anchored bytes. MA resolves this the same way it resolves everything else: never modify, always append. A **derivation record** registers a processed image as a new artifact carrying its own hash, the hash of the original it derives from, and the transformation identity (tool, version, parameters). The original captured bytes remain the evidence root in all cases; MA does not perform image processing itself, it only records verifiably that processing occurred.

A derivation record proves that a transformation was *declared* — not that it was *faithful*. An insider who dewarps a page and alters the marked bubbles in the same step produces a derivation that chains, anchors, and lies. Since most real OMR pipelines require preprocessing, this is the default path, not an edge case, and MA treats derivation verifiability as a recorded property — like capture class:

- **Reproducible derivation** — the transformation tool is deterministic and available: the verifier re-runs it on the anchored original and confirms the derived hash bit-for-bit. This is the required mode wherever the toolchain permits it.
- **Unverifiable derivation** — the transformation cannot be re-run (proprietary tool, non-deterministic processing): the verifier does **not** pass the result silently. It reports a distinct verdict — "result bound via unverifiable derivation" — so the weaker link is always visible. High-stakes deployments should add compensating controls: dual-party sign-off on derivation, or perceptual-similarity spot checks between original and derived images.

---

## Evidence Package

Each scanned artifact produces a cryptographic evidence package.

Example:

```text
Examination Artifact
├── pages/
│   ├── page_001.jpg
│   └── page_002.jpg
├── manifest.json          # metadata + ordered page hashes + page count, signed
├── manifest.sig
├── anchor_proof.json      # inclusion proof / TSA token / batch root path
├── result_record.json     # result hash + page hashes, signed (per binding profile)
├── result_record.sig
└── audit.log              # hash-chained; chain head anchored per batch
```

The example shows a complete (Phase 2+) package. Phase 1 packages omit the `.sig` files: they are unsigned but anchored. The anchor alone makes **modification** of an anchored package detectable — but it does not, by itself, establish origin or uniqueness. Phase 1's residual risk is therefore **substitution via parallel anchoring**: without signatures, nothing but operator say-so distinguishes a genuine package from a second, conflicting one anchored for the same roster entry. Phase 1 closes this structurally with the first-anchor-wins rule (see *Anchor strength and equivocation*): one batch record per pre-declared session identifier, at the deployment's single pre-declared anchor location, with any later conflicting record reported as a first-class failure. Deployments that can operate even a single deployment signing key in Phase 1 should do so — imperfect against the operator itself (see *Key custody*), but it makes creating a conflicting package attributable.

The evidence package allows anyone with the public verification material to determine whether the document has remained unchanged since the time of registration — **without** needing to trust the operating organization's storage, and without learning the candidate's identity.

Note that a bare hash file stored alongside the image provides no protection on its own; an attacker who can modify the image can regenerate the hash. Integrity derives from the signature and, above all, from the external anchor.

---

## Candidate Receipts

The strongest deterrent in the system is the candidate themselves. Where session logistics allow, the Scanner Gateway issues a receipt at scan time — the document manifest hash (covering all pages and the page count) plus the batch identifier and, where the salted-hash commitment scheme is used, the candidate's per-record salt (see *Privacy by Design*), printed or displayed to the candidate. Any candidate can later confirm that the document held in evidence — every page of it — is the one they saw scanned, shrinking the pre-registration trust window from the human side rather than the technical side.

Truncation is a security parameter, not a formatting choice: a short printed hash invites **grinding** — an operator crafts a doctored manifest until its truncated hash collides with the receipt already in the candidate's hands. Receipts therefore carry **at least 80 bits** of the manifest hash; where receipts are printed, a QR code carries the full 256-bit hash at no logistical cost and is the recommended form.

A receipt is useless without a way to check it, so the candidate verification path ships in Phase 1 alongside issuance — not in a later portal phase: the CLI verifier accepts a receipt directly, and each anchored batch publishes its list of manifest hashes so a candidate (or anyone assisting one) can confirm inclusion. Receipts also serve the system beyond the individual: every receipt in circulation is a distributed witness against anchor equivocation (see *Anchor strength and equivocation*).

A receipt contains no information useful to anyone but the candidate, so it introduces no privacy exposure. Receipt issuance belongs in Phase 1: verification demand comes from disputes, and disputes begin on day one.

---

## Handling Legitimate Rescans

Paper jams, smudged sheets, double-feeds, and candidate-requested reviews make rescanning a routine operation, and a system in which every legitimate rescan looks like tampering will not be adopted.

MA handles this with explicit supersession rather than replacement:

- Evidence packages are **never** overwritten or deleted.
- A rescan produces a new evidence package containing a `supersedes` field referencing the hash of the package it replaces, plus a recorded reason and operator identity.
- Both the superseded and superseding packages remain anchored and verifiable; the audit trail shows the full history.
- A verifier reports superseded packages as such — distinguishable from both "current" and "tampered."

Because supersession is the only sanctioned way to replace evidence, it is also the natural target for a malicious insider: a doctored sheet rescanned through the official process would verify perfectly. Supersession is therefore a constrained privilege, not a routine write:

- Supersession requires authorization by a second role, organizationally separate from the scanning operator — and the authorization is cryptographic, not clerical: the authorizer **signs the superseding manifest with their own distinct key**, and that signature is anchored with it. An unauthorized supersession is thereby unverifiable, not merely against policy.
- Supersession is valid only within a bounded window — normally the scanning session itself. Later replacement goes through a defined exception process, not an informal escalation: an **anchored exception record** naming the approver role and the reason, mandatory retention of the physical sheet behind the superseded document, and a distinct verifier verdict — "superseded out-of-window" — so late replacements are always visible as such.
- The verifier surfaces supersession rates per operator, per scanner, and per venue as anomaly signals. Rate anomalies catch volume abuse; they do not catch a single targeted supersession — which is why the authorizer signature and window rules above carry the load.
- Dual control within one organization mitigates a lone insider only. Two colluding insiders holding both roles can still push a doctored rescan through the official process; deployments where that risk is unacceptable should place the authorizer role outside the operating organization, for example with the supervising examination authority.

Tampering is thus defined precisely: any modification that does not go through the signed, anchored supersession process.

---

## Core Components

### MA Scanner Gateway

Receives images immediately after scanning.

Responsibilities:

- File normalization
- Metadata extraction
- Page hashing and document manifest construction (ordered page hashes, declared page count)
- Signature generation
- Batch roster commitment (one current package per roster entry, referencing the pre-anchored roster and session identifier — see *Trust Model*)
- Candidate receipt issuance
- Submission to the external anchor

Normalization happens strictly **before** hashing: the anchored hash is computed over the exact, final bytes of the image as stored, and no recompression, format conversion, or metadata rewrite may occur afterward. The canonical image format is fixed per deployment before the first scan.

### MA Manifest

Stores immutable metadata including:

- Candidate identifier (pseudonymous commitment — per-record salted hash or HMAC, per *Privacy by Design* — in any shared or published form)
- Exam ID
- Scan timestamp
- Scanner identifier
- Capture class (scanning station or client capture) and device attestation evidence, where applicable
- Ordered page hash list and declared page count
- Digital signature
- Supersession reference (if a rescan)
- Software version

### MA Result Binder

Runs after processing. Produces a signed record containing:

- Result hash
- The page hashes the result was derived from
- Processing timestamp

Two binding profiles cover the two ways examination artifacts are marked:

- **Automated** (OMR and similar software): the record additionally carries the software identifier and version, **and the hashes of every processing input — answer key, form template, and scoring configuration**. A score is a function of the image *and* those inputs; a record that omits them permits silent score manipulation through a substituted answer key while every hash still verifies. The answer key itself is anchored once the examination ends, so the key used for scoring is publicly fixed. The guarantee is **reproducibility** — a verifier re-runs the software on the anchored images with the committed inputs and confirms the result. Where the software is proprietary or non-deterministic and cannot be re-run, the verifier reports the distinct verdict "result not reproducible by verifier" rather than passing silently; the input commitments still constrain what the operator can later claim was used.
- **Attested** (human grading): the record additionally carries the grader identity and rubric version, and is signed by the grader with an **individually enrolled key**. The result cannot be recomputed from the images; the guarantee is **attribution** — this grader's key committed to this result over exactly these page hashes. That phrasing is deliberately precise: a signature proves key commitment, not human attention. Grader keys are enrolled per individual, held outside the operator's score-processing role, and revocable; procedurally, signing happens only from the grading interface that displays the anchored images. If the operator issues and stores grader keys, attribution collapses back into operator trust — key custody guidance treats this as a deployment error.

Where processing required a derived image (see *Artifact and Document Model*), the result record references the derived hash, and verification follows the derivation chain back to the anchored original — reproducibly or with the "unverifiable derivation" verdict, per the derivation's recorded verifiability.

Result records are anchored through the same batch mechanism as manifests; a signature alone would leave them rewritable by the operating organization.

Result records also inherit the completeness and supersession discipline of documents: **exactly one current result record per current document**. Multiple non-superseded result records for the same document is a first-class verification failure — result-level equivocation, not an oddity. Legitimate re-grades and appeals use **result supersession**: a new result record carrying a `supersedes` reference, authorized and signed by a second role exactly as document supersession is, and anchored. The full grading history remains verifiable.

### MA Verifier

Verifies:

- Image integrity against the manifest
- Signature validity
- Anchor inclusion (the manifest existed, unmodified, at anchor time)
- Anchor uniqueness (exactly one batch record per pre-declared session identifier, at the pre-declared anchor location; a conflicting record is a first-class failure)
- Session-registry completeness (every pre-anchored session has an anchored batch within its declared window)
- Scan-to-anchor latency (against the deployment's declared maximum)
- Roster completeness (every roster entry has exactly one current, anchored package, against the pre-anchored roster)
- Document completeness (all declared pages present, in the declared order)
- Result-to-image binding (reproducible for the automated profile — including the committed answer key, template, and configuration — attributed for the attested profile)
- Result uniqueness (exactly one current result record per current document)
- Derivation chain consistency (every processed image chains back to an anchored original; reproducible derivations re-run and confirmed, unverifiable ones reported as a distinct verdict)
- Supersession chain consistency (authorizer signatures, window bounds, anchored exception records, and supersession-rate anomaly reporting)
- Candidate receipts (a receipt is directly checkable against the anchored batch)
- Audit-log chain integrity (hash chain intact and matching the anchored chain head)
- Timestamp validity
- Chain of custody

Verifier verdicts are deliberately multi-valued: "current," "superseded," "superseded out-of-window," "bound via unverifiable derivation," "result not reproducible by verifier," and "tampered" are distinct outcomes. Collapsing them into pass/fail would hide exactly the distinctions the trust model exists to surface.

### MA Audit

Records every operation performed on examination evidence.

Examples:

- Scan completed
- Batch anchored
- Verification performed
- Rescan / supersession recorded
- Export requested
- Archive created
- Evidence accessed

An audit log the operator can rewrite undermines the chain-of-custody claim it exists to support, so the trail must itself be tamper-evident. Audit entries are **hash-chained**, and the current chain head is folded into every batch anchor — audit history rides the same anchoring mechanism as manifests and result records. A rewritten or deleted entry breaks the chain against the anchored head.

---

## Prior Art and Standards

MA deliberately builds on existing, audited mechanisms:

- **C2PA (Coalition for Content Provenance and Authenticity)** — the emerging standard for signed content provenance in images, designed for signing at the point of capture. MA's evidence generator aligns with the C2PA manifest model where practical, and C2PA-capable scanners can shrink the pre-registration trust window to the device itself.
- **RFC 3161 Timestamp Authorities** — independent proof that a hash existed at a point in time; the simplest form of external anchoring and a Phase 1 requirement, not a later enhancement.
- **Sigstore / Rekor** — an existing open-source transparency log providing append-only, publicly verifiable inclusion proofs; the reference implementation for MA's transparency log rather than custom infrastructure.
- **Merkle tree batch commitments** — where no online anchor is available at scan time, a per-batch Merkle root published through a hard-to-rewrite channel provides a minimal viable anchor. This is the weakest anchor class (see *Anchor strength and equivocation*): it requires independent archival witnesses or an issue-numbered channel (official gazette, newspaper), not merely the operator's own website.

---

## Security Goals

Project MA aims to provide:

- Tamper evidence
- Completeness at roster, document, and page level (omission is as detectable as modification)
- Authenticity
- Integrity
- Non-repudiation (via external anchoring, per the Trust Model)
- Traceability
- Chain of custody

The project explicitly does **not** attempt to:

- replace OMR software or grading workflows,
- perform image processing (deskew, dewarp, enhancement) — preparing images for recognition is the OMR vendor's domain; MA only records, via derivation records, that such processing occurred,
- serve as general-purpose digital-asset notarization — generic content integrity is already well served by C2PA, OpenTimestamps, and public transparency logs; MA's scope is the examination domain, where roster completeness, supersession discipline, receipts, and result binding are the differentiators,
- prove correspondence between a scanned image and the physical sheet (a procedural matter — see Trust Model),
- prevent insider attacks entirely,
- eliminate operational fraud through technology alone.

Instead, MA increases the probability that unauthorized modifications will be detected, and makes the boundary of that guarantee explicit.

---

## Roadmap

External anchoring appears in Phase 1 by design: hashing and signing without an anchor the operator cannot rewrite would create the appearance of integrity without the substance, which in a high-stakes context is worse than nothing.

### Phase 1 — Minimum Detectable Tampering

- SHA-256 page hashing and document manifest generation (unsigned; integrity from the anchor; optional single deployment signing key for origin attribution)
- Roster pre-commitment and session registry (roster and session/venue schedule anchored before the session by the registration authority)
- Batch Merkle commitments with external publication, bound to the pre-anchored roster, with first-anchor-wins uniqueness per session identifier
- Minimal supersession (`supersedes` field, anchored, with a defined verifier verdict for duplicate packages) — rescans are routine from day one, so the verdict for them cannot wait for Phase 2
- Candidate receipt issuance (≥ 80-bit truncation or full-hash QR; carries the per-record salt) and Phase 1 receipt verification (CLI verifier + published batch hash lists)
- RFC 3161 timestamp support (anchor class recorded; equivocation caveats per the Trust Model)
- Hash-chained audit log, chain head anchored per batch
- CLI verifier (integrity + anchor inclusion and uniqueness + session-registry, roster, document, and page completeness), reproducible builds
- Image comparison

### Phase 2 — Signatures and Binding

- Digital signatures and key management guidance (HSM/smartcard, separation of duties)
- Result binding, automated profile (OMR), with committed processing inputs (answer key, template, configuration) and post-exam answer-key anchoring, anchored alongside manifests
- Result uniqueness and result supersession (re-grades and appeals)
- Derivation records (processed images bound to anchored originals; reproducible where the toolchain permits, distinct verdict otherwise)
- Full supersession / rescan workflow (dual authorization with anchored authorizer signatures, bounded window, anchored exception records, anomaly reporting)
- PDF verification

### Phase 3 — Integration

- Attested binding profile (human grading: grader-signed result records, individual key enrollment and revocation)
- Transparency log integration (Sigstore/Rekor)
- Multi-party verification
- API integration
- CI/CD support

### Phase 4 — Ecosystem

- C2PA manifest interoperability
- Mobile / web capture clients (in-app capture, on-device hashing, device attestation; C2PA-aligned)
- Blockchain-optional anchoring
- Public verification portal (privacy-preserving; pseudonymous identifiers only)

---

## Target Users

- Government examination agencies
- Universities
- Professional certification bodies
- Civil service examination providers
- Independent auditors
- Digital forensic investigators

---

## Open Source Philosophy

Project MA will prioritize:

- transparency,
- reproducibility,
- cryptographic correctness,
- independent verification,
- standards compliance.

Security should never depend upon secrecy.

---

## Motto

> **Truth cannot be rewritten.**

---

## Name Origin

### Ma'at

Ancient Egyptian goddess representing Truth, Justice, Order, and Balance.

### Aletheia

Ancient Greek concept meaning "Truth Unconcealed" or "The Revealing of Truth."

Together they represent the project's philosophy:

> **Justice through Verifiable Truth.**
