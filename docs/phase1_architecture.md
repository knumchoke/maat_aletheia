# Ma'at.Aletheia — Phase 1 Architecture

**Status:** Draft for review
**Date:** 2026-07-05
**Basis:** [project_proposal.md](project_proposal.md) v0.6 (post security review), [handoff.md](handoff.md)
**Scope contract:** Proposal → Roadmap → Phase 1 ("Minimum Detectable Tampering")

This document turns the reviewed v0.6 trust model into a concrete Phase 1 architecture. It does not re-litigate the trust model. Every design element below is traceable to a proposal section or a review finding (C-*/H-*/M-*/L-*), and every element is checked against the eight non-negotiable invariants (handoff §4), referenced as **Inv-1** … **Inv-8**:

| # | Invariant (short form) |
|---|---|
| Inv-1 | Never modify, always append |
| Inv-2 | Anchor before processing |
| Inv-3 | Hash the final bytes; canonical format fixed pre-deployment |
| Inv-4 | The anchor, not the operator's key, is the load-bearing control |
| Inv-5 | First anchor wins; one batch record per pre-declared session identifier |
| Inv-6 | Verifier verdicts are multi-valued, never pass/fail |
| Inv-7 | Nothing published leaks candidate identity |
| Inv-8 | Roster and schedule anchored pre-session by a role separate from scanning |

---

## 1. Assumptions and resolved ambiguities

Stated explicitly, per the handoff's instruction. Items marked **(maintainer)** should be confirmed by the proposal maintainer; the architecture proceeds on the stated resolution.

- **A-1. Session identifier granularity (maintainer).** The proposal defines a batch as "one anchored commitment per (session, venue) pair, under a session identifier pre-declared in the session registry" (Trust Model → Completeness; closes L-2). It is ambiguous whether one session identifier may span multiple venues. **Resolution:** the session registry issues **one record per (session, venue) pair**, and `session_id` uniquely encodes that pair (e.g. `NT2026-M6-MATH:BKK-014:AM`). Consequences: `session_id` *is* the batch identifier; first-anchor-wins (Inv-5) needs no secondary venue disambiguation; the receipt can name its batch at scan time even though the batch record does not yet exist (see A-2).
- **A-2. "Batch identifier" on receipts.** The receipt is issued at scan time (Candidate Receipts), but the batch record and its hash exist only at session close. **Resolution:** the receipt carries `session_id` (which, by A-1, identifies exactly one legitimate batch) — not a batch record hash.
- **A-3. Candidate commitment scheme in Phase 1.** Privacy by Design permits per-record salted hash or per-session HMAC (M-5). Phase 1 uses the **per-record salted hash** scheme: the roster pre-commitment flow (C-1) requires the registration authority to generate commitments before the exam, and the salt must reach the candidate on the receipt (M-3, M-5). Per-session HMAC remains schema-supported (`scheme` field) for deployments with a separate HMAC key custodian; it is not the Phase 1 default because Phase 1 has no independent key-holder role yet.
- **A-4. `scan_time` is operator-asserted.** The scan-to-anchor latency check (M-1) compares an operator-supplied `scan_time` to an externally attested anchor time. An operator could post-date `scan_time` to shrink an embarrassing window. Mitigations in this architecture: (a) the verifier additionally checks `scan_time` falls inside the pre-anchored scheduled session window (Inv-8 material is externally fixed); (b) receipts fix the manifest hash in candidates' hands at true scan time; (c) the batch record carries `first_scan_time`/`last_scan_time` so gross inconsistencies surface. Residual risk is documented in §7 (T-9).
- **A-5. Canonical image format.** Fixed per deployment before the first scan (Inv-3). This architecture recommends **PNG (lossless)** for scanning stations; the deployment's choice is declared in the public verification material. MA never converts after hashing; if the scanner emits JPEG and the deployment declared JPEG, the JPEG bytes as first written are the final bytes.
- **A-6. "Image comparison" (Phase 1 roadmap bullet) (maintainer).** The proposal lists "Image comparison" in Phase 1 without definition. **Resolution adopted here:** a verifier subcommand that byte-compares (and reports pixel-level diffs of) a presented image against the stored/anchored one, as a dispute-support tool. It is *not* perceptual similarity checking (that is Phase 2+ compensating-control territory per Artifact and Document Model). Confirm intent.
- **A-7. One audit chain per gateway instance.** The proposal requires the chain head folded into every batch anchor (M-7) but does not fix chain topology. **Resolution:** one hash chain per Scanner Gateway instance (per venue scanning station), chain identity recorded in each entry and in the batch record. Cross-gateway ordering is provided by the anchors, not by a merged chain.
- **A-8. Registration authority also uses MA anchor adapters.** Session registry records are anchored with the same anchor classes and the same first-anchor-wins semantics as batches (anchored *pre-session*, Inv-8).

---

## 2. Deployment topology and trust boundaries

### 2.1 Roles (who runs what)

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ REGISTRATION AUTHORITY (RA) — organizationally separate from the operator   │
│   runs: ma-registry CLI (session registry / roster pre-commitment)          │
│   holds: candidate roster + per-record salts (pre-exam), RA workstation     │
│   publishes: anchored session records; contributes to PVM bundle            │
└───────────────┬─────────────────────────────────────────────────────────────┘
                │ session pack (session record + commitment/salt table)
                │ delivered pre-session, integrity-checkable against anchor
                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ OPERATOR — scanning organization (the primary adversary in the trust model) │
│   runs: ma-gateway (per venue), evidence store, audit chain,                │
│         ma anchor submit (or delegates submission, §5.6)                    │
│   holds: page images (restricted), optional deployment signing key          │
└───────┬──────────────────────────────┬──────────────────────────────────────┘
        │ 32-byte roots + records      │ receipts (QR) at scan time
        ▼                              ▼
┌───────────────────────┐    ┌─────────────────────────┐
│ EXTERNAL ANCHORS      │    │ CANDIDATES              │
│  RFC 3161 TSA (req.)  │    │  distributed witnesses  │
│  witnessed root (fbk) │    │  (Trust Model)          │
│  Rekor (Phase 3)      │    └─────────────────────────┘
└───────────────────────┘
┌─────────────────────────────────────────────────────────────────────────────┐
│ PVM PUBLISHER — regulator / supervising authority / project infra (M-6)     │
│   publishes: public verification material bundle, batch hash lists,         │
│              batch records + anchor proofs (mirrored)                       │
│   MUST NOT be the operator                                                  │
└─────────────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────────────┐
│ VERIFIERS — anyone: auditors, candidates, forensic investigators            │
│   runs: ma-verify CLI (reproducible build)                                  │
│   inputs: PVM bundle, evidence package(s) and/or receipt                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Trust boundaries, drawn explicitly

1. **RA ↔ Operator (Inv-8, C-1).** The roster and schedule are fixed by the RA and anchored before the operator scans anything. The operator receives the session pack read-only; it cannot alter the roster without breaking the pre-anchored commitment. The RA never touches page images.
2. **Operator ↔ Anchor (Inv-2, Inv-4, H-1).** Everything downstream of the anchor is verifiable without trusting the operator. The anchor endpoint(s) are pre-declared in the PVM; the operator cannot substitute a friendlier anchor without the verifier noticing.
3. **Operator ↔ Candidate (M-3).** The receipt crosses this boundary at scan time and is the only control reaching back before anchor time.
4. **Operator ↔ Verifier / PVM publisher (M-6).** The verifier and its trust inputs never come from the operator. PVM bundle integrity is independent of operator infrastructure.
5. **Inside the operator (pre-registration window).** Between platen and gateway hash, bytes are cryptographically unconstrained (Trust Model → What MA cannot prove). This is procedural territory; the architecture's job is to keep the window measurable (M-1) and small (§5.1).

Collusion limits (stated honestly, per proposal): RA + operator colluding can drop a candidate pre-commitment (T-3 in §7); this is outside what Phase 1 cryptography can detect and is why the RA is placed with the supervising authority where stakes demand it.

---

## 3. Schemas and formats (designed first, per handoff §5.2)

### 3.1 Conventions (all records)

- **Encoding:** UTF-8 JSON. **Record hash** = hash of the RFC 8785 (JCS) canonical serialization of the record. JCS is chosen over ad-hoc canonicalization because it is a published RFC with multiple interoperable implementations — a verifier reimplementation must reproduce hashes bit-for-bit.
- **Hash values** are strings `"<alg>:<lowercase-hex>"`, e.g. `"sha256:ab12…"`. The algorithm identifier is part of the value everywhere a hash appears — field-level algorithm agility (L-1), not just record-level.
- **Schema identifiers:** every record carries `"schema": "ma/<type>/v1"`. Verifiers reject unknown major versions with the distinct verdict `unsupported-schema` (never silently pass — Inv-6).
- **`hash_alg`** declares the record's own hashing algorithm (the one used to compute this record's hash and, by default, all hashes it contains). Phase 1 fixes `sha256`; the field exists so succession (L-1, §8.1) is a data change, not a format change.
- **Timestamps:** RFC 3339 UTC. **Durations:** ISO 8601 (e.g. `"PT4H"`).
- **Signatures** (optional in Phase 1, H-2) are detached: `<record>.sig` contains `{schema:"ma/sig/v1", key_fingerprint, alg:"ed25519", sig_b64}` over the record's JCS bytes. Signatures are never folded into the record being hashed — the anchored hash is signature-independent (Inv-4: the anchor carries detection; the signature only adds attribution).
- **`ext` field:** every record type reserves an `ext` object for forward-compatible extension (C2PA interop, §8.2). Verifiers hash it (it is part of the bytes) but ignore unknown members.

### 3.2 Document manifest — `ma/manifest/v1`

Per *MA Manifest*, with algorithm agility (L-1) and capture class (Trust Model → Capture clients).

```json
{
  "schema": "ma/manifest/v1",
  "hash_alg": "sha256",
  "deployment_id": "th-nt-2026",
  "session_id": "NT2026-M6-MATH:BKK-014:AM",
  "exam_id": "NT2026-M6-MATH",
  "candidate_commitment": {
    "scheme": "salted-hash-v1",
    "value": "sha256:7f3a09c1…"
  },
  "capture": {
    "class": "scanning_station",
    "scanner_id": "BKK-014-SCN-02",
    "operator_id": "op:4471",
    "software": { "name": "ma-gateway", "version": "1.0.3" }
  },
  "scan_time": "2026-07-05T02:14:31Z",
  "page_count": 2,
  "pages": [
    { "index": 1, "hash": "sha256:9d1e44aa…", "media_type": "image/png" },
    { "index": 2, "hash": "sha256:0c8b17f2…", "media_type": "image/png" }
  ],
  "supersedes": null,
  "ext": {}
}
```

- `candidate_commitment.value` = `sha256(salt_bytes || candidate_id_utf8)` with a 128-bit random per-record salt generated by the **RA** at roster build time (see §3.4 — the manifest commitment must equal the roster entry commitment, or roster-completeness matching would be impossible). Schemes: `salted-hash-v1`, `hmac-sha256-session-v1` (A-3). High-entropy salt defeats enumeration of the small candidate-ID space (Privacy by Design); the salt goes to the candidate on the receipt (M-5), so their self-verification is not an operator favor. Published artifacts contain only `value` — Inv-7.
- `pages[].hash` is over the exact stored bytes; `media_type` must equal the deployment's declared canonical format (Inv-3, A-5).
- **Manifest hash** = hash of JCS bytes; this is the value on the receipt, in the batch entry list, and in the published hash list.
- `supersedes` (minimal Phase 1 form, M-9/M-4) when non-null:

```json
"supersedes": {
  "manifest_hash": "sha256:e11d02b7…",
  "reason": "double-feed, pages misordered",
  "operator_id": "op:4471",
  "authorized_by": null
}
```

`authorized_by` is null in Phase 1 (the full dual-authorization workflow is Phase 2, M-4); the field exists now so Phase 2 authorizer signatures do not require a schema major-version bump. Phase 1 discipline: a superseding manifest must land **in the same batch** as, or a later anchored batch than, its target; two non-superseded manifests for the same `candidate_commitment` in one session yield the verdict `duplicate-package` (first-class failure, Inv-5 applied at package level, H-2).

### 3.3 Batch record — `ma/batch/v1`

One per `session_id` (A-1, Inv-5). This record's JCS hash is what gets anchored.

```json
{
  "schema": "ma/batch/v1",
  "hash_alg": "sha256",
  "deployment_id": "th-nt-2026",
  "session_id": "NT2026-M6-MATH:BKK-014:AM",
  "session_record_hash": "sha256:41ffae02…",
  "roster_commitment": "sha256:b3c9d1e0…",
  "tree": {
    "style": "rfc6962-sha256",
    "size": 312,
    "root": "sha256:66a0f4d9…"
  },
  "entries": [
    { "index": 0, "type": "manifest", "hash": "sha256:5b21c4dd…" },
    { "index": 1, "type": "manifest", "hash": "sha256:9d00e1a3…" }
  ],
  "first_scan_time": "2026-07-05T01:03:12Z",
  "last_scan_time": "2026-07-05T04:47:55Z",
  "sealed_time": "2026-07-05T04:52:10Z",
  "audit": {
    "chain_id": "BKK-014-GW-01",
    "head": "sha256:cc1902ab…",
    "head_seq": 8412
  },
  "gateway": { "software": "ma-gateway", "version": "1.0.3" },
  "ext": {}
}
```

- `session_record_hash` + `roster_commitment` bind the batch to the *pre-anchored* registry record (C-1, Inv-8). The verifier checks both directions: every roster commitment has exactly one current manifest, and every anchored session record has an anchored batch inside its window.
- `entries` lists every evidence record in scan order; `type` is `manifest` only in Phase 1, and future types (`result`, `exception`, `reanchor`) are additive. The Merkle leaves are derived from these entries (§5.2).
- `audit.head` folds the gateway's audit chain into the anchor (M-7).
- The batch record contains **no candidate data and no page hashes** — it is publishable as-is (Inv-7).

### 3.4 Session registry record — `ma/session/v1`

Produced and anchored by the RA **before** the session (Inv-8, C-1).

```json
{
  "schema": "ma/session/v1",
  "hash_alg": "sha256",
  "deployment_id": "th-nt-2026",
  "session_id": "NT2026-M6-MATH:BKK-014:AM",
  "exam_id": "NT2026-M6-MATH",
  "venue_id": "BKK-014",
  "scheduled_window": {
    "start": "2026-07-05T01:00:00Z",
    "end": "2026-07-05T05:00:00Z"
  },
  "roster": {
    "scheme": "salted-hash-v1",
    "commitment": "sha256:b3c9d1e0…",
    "count": 312,
    "tree_style": "rfc6962-sha256"
  },
  "anchoring": {
    "max_scan_to_anchor": "PT4H",
    "batch_deadline": "2026-07-05T09:00:00Z"
  },
  "registrar_id": "ra:onet-central",
  "ext": {}
}
```

- `roster.commitment` is the RFC 6962 root over the **sorted** (lexicographic by hex) list of candidate commitment values; `count` makes suppression-by-shorter-roster impossible. Sorting makes the roster tree order-independent of registration sequence and lets the RA hand candidates individual membership proofs later without leaking roster order.
- `anchoring.max_scan_to_anchor` may tighten (never loosen) the deployment-wide default in the PVM — offline venues get their window declared *up front*, where verifiers can see it (M-1, §5.6). `batch_deadline` is the absolute time after which a missing batch becomes the `session-without-batch` failure (whole-batch suppression, C-1).
- The RA also produces the **session pack** for the operator (not anchored, restricted): the session record + the table `[{candidate_commitment, salt, candidate_id}]`. The gateway needs it to match scans to roster entries and to print salts on receipts. Operator knowledge of salts is consistent with the trust model: pseudonymity holds against external verifiers, not against the operator (M-5).

### 3.5 Candidate receipt — QR payload `MA1`

Full 256-bit manifest hash (M-3: grinding resistance; no truncation in the QR), per-record salt (M-5), batch identity (A-2). Compact key-value form to keep the QR at version ≤ 10 for reliable thermal printing:

```text
MA1;d=th-nt-2026;s=NT2026-M6-MATH:BKK-014:AM;m=sha256:5b21c4dd…(64 hex);r=BASE64URL(16-byte salt);t=2026-07-05T02:14:31Z
```

- `m` — full manifest hash. `r` — per-record salt (present only under `salted-hash-v1`). `t` — scan time as shown to the candidate.
- Printed human-readable fallback beneath the QR: the first **20 hex characters (80 bits)** of the manifest hash — the proposal's floor (M-3) — plus `session_id`.
- `ma-verify receipt <payload|image>` consumes this directly (Phase 1 requirement: verification path ships with issuance).
- Privacy: the receipt reveals the salt and manifest hash to its holder only; the manifest itself is not published, so receipt + published hash list still identifies no one but the holder (Inv-7).

### 3.6 Anchor proof — `ma/anchor-proof/v1`

Anchor class is a per-batch recorded property the verifier weighs, never hides (H-1, Inv-6).

```json
{
  "schema": "ma/anchor-proof/v1",
  "anchored_hash": "sha256:1f4ce090…",
  "anchored_record": "ma/batch/v1",
  "anchor_class": "tsa-rfc3161",
  "anchor_time": "2026-07-05T05:03:44Z",
  "tsa": {
    "endpoint": "https://tsa.example.org/tsr",
    "token_b64": "MIIK…",
    "hash_alg_in_token": "sha256"
  },
  "witnessed_root": null,
  "reanchors": [],
  "ext": {}
}
```

For the fallback class:

```json
"anchor_class": "witnessed-root",
"witnessed_root": {
  "publication": {
    "channel": "gazette",
    "reference": "Royal Gazette vol. 143 pt. 58, 2026-07-06, p. 112",
    "published_value": "sha256:1f4ce090…"
  },
  "witnesses": [
    { "type": "web-archive", "url": "https://web.archive.org/…", "captured": "2026-07-06T02:00:00Z" },
    { "type": "third-party-archiver", "id": "perma.cc", "ref": "https://perma.cc/…" }
  ]
}
```

`anchor_time` for `witnessed-root` is the publication issue time — coarser than a TSA token, which the verifier reports as such when checking latency (Inv-6: coarse evidence is surfaced, not rounded). `reanchors` is the L-1 hook (§8.1). Phase 3 adds `anchor_class:"transparency-log"` with `{log_id, log_index, inclusion_proof, sth}` — the field shape is reserved now so adapters share one proof record (§4.3).

### 3.7 Inclusion proof (per document) — `ma/inclusion-proof/v1`

```json
{
  "schema": "ma/inclusion-proof/v1",
  "tree_style": "rfc6962-sha256",
  "batch_record_hash": "sha256:1f4ce090…",
  "leaf_index": 17,
  "tree_size": 312,
  "leaf_hash": "sha256:aa90cc12…",
  "path": ["sha256:03df…", "sha256:71bb…", "sha256:e2a4…"]
}
```

Leaf construction is defined in §5.2. The proof lets a single evidence package be verified against the anchored batch root without the other 311 packages (and without their manifests — Inv-7).

### 3.8 Public verification material bundle — `ma/pvm/v1`

The versioned artifact of Trust Model → Public verification material (M-6).

```json
{
  "schema": "ma/pvm/v1",
  "bundle_version": 7,
  "previous_bundle_hash": "sha256:90ab31fe…",
  "deployment_id": "th-nt-2026",
  "issued": "2026-06-01T00:00:00Z",
  "issuer": "ra:onet-central",
  "hash_algs": ["sha256"],
  "canonical_image_format": "image/png",
  "max_scan_to_anchor": "PT4H",
  "anchor": {
    "declared_class": "tsa-rfc3161",
    "fallback_class": "witnessed-root",
    "tsa": {
      "endpoints": ["https://tsa.example.org/tsr"],
      "ca_certs_pem": ["-----BEGIN CERTIFICATE-----…"]
    },
    "witnessed_root_channels": [
      { "channel": "gazette", "id": "royal-gazette" }
    ]
  },
  "keys": [
    {
      "role": "deployment-signing",
      "alg": "ed25519",
      "fingerprint": "sha256:77e1b0c4…",
      "public_key_b64": "…",
      "valid_from": "2026-06-01T00:00:00Z",
      "valid_to": null,
      "revoked": null
    }
  ],
  "session_registry": {
    "records": [
      { "session_id": "NT2026-M6-MATH:BKK-014:AM",
        "record_hash": "sha256:41ffae02…",
        "anchor_proof_hash": "sha256:8812d0aa…" }
    ]
  },
  "verifier_builds": [
    { "version": "1.0.3", "artifact_hash": "sha256:12dd09b3…", "source_tag": "v1.0.3" }
  ],
  "ext": {}
}
```

**Publication channel and rotation semantics (decision):**

- The bundle is published by the **PVM publisher** (regulator / supervising authority / project infrastructure — never the operator, M-6) as a static, mirrorable artifact: a public git repository plus a plain HTTPS directory. Git gives free history and independent clones; the plain directory serves verifiers without git.
- **Every bundle version is itself anchored** (its JCS hash TSA-timestamped, or gazette-published for the fallback class) and **hash-chained** via `previous_bundle_hash`. A verifier that has ever pinned any bundle version can detect a rewritten history — the same equivocation logic the anchors use, applied to the trust inputs themselves.
- `bundle_version` is strictly monotonic. **Update semantics: append-only** (Inv-1 applied to trust material): new sessions, new keys, and revocations are added in a new version; nothing is removed. Key rotation = new key entry with `valid_from`, old entry gains `valid_to`; compromise = `revoked: {time, reason}` — records signed in the validity window remain evaluable, and the verifier reports signatures under revoked keys distinctly (Inv-6).
- Verifiers pin `(deployment_id, a known bundle hash)` on first use and accept only descendants of it.

### 3.9 Published batch hash list — `ma/hashlist/v1`

Phase 1 receipt-verification path (M-3): per batch, published on the PVM publisher's channel alongside the batch record and anchor proof.

```json
{
  "schema": "ma/hashlist/v1",
  "session_id": "NT2026-M6-MATH:BKK-014:AM",
  "batch_record_hash": "sha256:1f4ce090…",
  "manifest_hashes": ["sha256:5b21c4dd…", "sha256:9d00e1a3…"]
}
```

Contains manifest hashes only — no commitments, no page hashes — so publication is Inv-7-clean, and a candidate can grep for their receipt's `m` value.

### 3.10 Audit chain entry — `ma/audit/v1`

Hash-chained JSONL (M-7): each line's `prev` is the JCS hash of the previous line; genesis `prev` is `sha256:0…0`.

```json
{
  "schema": "ma/audit/v1",
  "chain_id": "BKK-014-GW-01",
  "seq": 8411,
  "prev": "sha256:aa02cd19…",
  "time": "2026-07-05T02:14:32Z",
  "event": "scan.completed",
  "detail": { "manifest_hash": "sha256:5b21c4dd…", "operator_id": "op:4471" },
  "ext": {}
}
```

Event vocabulary (Phase 1): `session.pack_loaded`, `scan.completed`, `receipt.issued`, `package.superseded`, `batch.sealed`, `anchor.submitted`, `anchor.confirmed`, `evidence.accessed`, `export.requested`. `detail` must never contain candidate identifiers, only commitments/hashes (Inv-7) — the audit chain head is anchored and lines may be disclosed to auditors.

---

## 4. Components (handoff §5.1)

### 4.1 Session Registry / Roster Pre-commitment — `ma-registry`

**Form (decision):** a standalone **CLI**, not a service. Rationale: the RA is organizationally separate (Inv-8) and may be a regulator with strict IT constraints; a CLI on an RA-controlled (optionally air-gapped) workstation minimizes the RA's operational surface and makes the "separate role" claim real rather than a deployment diagram fiction. Alternative considered: a hosted registry service — rejected for Phase 1 because whoever hosts it becomes a trust concentration and, in practice, hosting would drift to the operator.

**Responsibilities:** build roster commitments (generate per-record salts, compute commitment values, build the sorted RFC 6962 roster tree); emit `ma/session/v1` records; anchor them (via the anchor adapters, A-8) *before* `scheduled_window.start`; emit the session pack for the operator; contribute session record hashes to the PVM bundle.

**Interfaces:**
- `ma-registry session create --roster roster.csv --session-id … --window … --max-latency …` → `session_record.json`, `session_pack.tar` (restricted), `roster_proofs/` (optional per-candidate membership proofs).
- `ma-registry anchor submit session_record.json` → `anchor_proof.json`.

**Data in:** candidate roster (IDs — the only component besides the gateway that ever sees them), schedule. **Data out:** anchored session records (public), session pack (to operator, restricted), salt table entries (to candidates only via receipts, through the pack).

**Invariants upheld:** Inv-8 (pre-session anchoring by a separate role); Inv-7 (only commitments leave the RA); Inv-5 (session identifiers are pre-declared here, giving first-anchor-wins its namespace); C-1 (roster provenance fixed before scanning exists).

### 4.2 Scanner Gateway — `ma-gateway`

The operator-side pipeline: ingest → normalize → hash → manifest → receipt → batch → seal → (anchor submission, possibly delegated).

**Responsibilities** (per *MA Scanner Gateway*): watch/receive scanner output (§5.1); enforce canonical format (Inv-3 — a scan in the wrong format is **rejected at ingest**, never converted after hashing; conversion, if the deployment permits it, happens before the bytes are considered final); compute page hashes and build `ma/manifest/v1`; match the scan to a roster entry from the session pack and attach the pre-committed `candidate_commitment`; issue the receipt (§4.4) synchronously with manifest creation; record supersession when an operator invokes rescan; append audit entries for every action; at session close, **seal** the batch (freeze `entries`, compute the Merkle root, emit `ma/batch/v1` + per-document inclusion proofs + `ma/hashlist/v1`); enqueue the batch record hash for anchoring (§5.6).

**Interfaces:**
- Ingest: watch folder + `ma-gateway ingest <files> --candidate <id>` (decision and M-1 analysis in §5.1).
- `ma-gateway session open <session_pack>` / `ma-gateway session seal`.
- `ma-gateway rescan --supersedes <manifest_hash> --reason …`.

**Data in:** page images, session pack. **Data out:** evidence packages (restricted store, §5.3), receipts, sealed batch records (public), audit chain.

**Invariants upheld:** Inv-1 (the gateway has no update or delete code path — the store API is create-only; rescans create superseding manifests); Inv-2/Inv-5 (seal freezes exactly one batch per session_id before any processing consumes images); Inv-3 (normalization strictly precedes hashing; reject-don't-convert after ingest); Inv-7 (everything the gateway emits beyond the operator boundary carries commitments only).

**Explicitly out of scope for the gateway:** OMR, grading, image processing (proposal → Security Goals). It fixes identity; it never interprets content.

### 4.3 Anchor adapters

One interface, three classes, class always recorded (H-1). The abstraction is deliberately narrow so Rekor drops in at Phase 3 without touching callers:

```go
type AnchorAdapter interface {
    // Submit anchors a record hash. Idempotent per (deploymentID, sessionID):
    // resubmission of the same hash returns the existing proof; submission of a
    // DIFFERENT hash for an already-anchored sessionID fails locally — the
    // gateway must never be the origin of an equivocation (Inv-5).
    Submit(ctx, deploymentID, sessionID string, recordHash Hash) (AnchorProof, error)
    // Verify checks a proof against the PVM (endpoints, keys, class rules).
    Verify(proof AnchorProof, recordHash Hash, pvm PVM) AnchorVerdict
    Class() AnchorClass   // "tsa-rfc3161" | "witnessed-root" | "transparency-log"
}
```

- **`tsa-rfc3161` (Phase 1 required):** submits the batch record hash in a TimeStampReq; stores the full DER token and TSA chain in the proof. `Verify` validates the token signature against PVM `ca_certs_pem`, matches `MessageImprint` to the record hash, and extracts `genTime`. Local `Submit` idempotence cannot stop a *malicious* operator timestamping two records (TSA proves existence, not uniqueness — proposal → Anchor strength); uniqueness is enforced verifier-side: any two valid batch records for one `session_id` = `anchor-conflict`, first (earliest `genTime`) wins (Inv-5).
- **`witnessed-root` (Phase 1 fallback):** emits the value to publish plus a checklist artifact; `Submit` completes only when the operator supplies the publication reference and ≥ the PVM-declared minimum witness captures. Acceptable channels are pre-declared in the PVM (issue-numbered gazette/newspaper, or ≥2 independent archivers), per the proposal's Prior Art constraints.
- **`transparency-log` (Phase 3, anticipated now):** `Submit` will return an inclusion proof + signed tree head; `Verify` will check consistency against witnessed STHs. The proof record shape (§3.6) and the RFC 6962 tree choice (§5.2) are the anticipation — no Phase 1 data will need re-encoding to gain Rekor anchoring later, only re-anchoring records appended (§8.1).

**Invariants upheld:** Inv-4 (adapters are the only path by which detection authority enters the system); Inv-5 (idempotent submit + verifier-side first-anchor-wins); Inv-2 (gateway blocks "session complete" status until `anchor.confirmed` or the declared window is flagged as exceeded).

### 4.4 Receipt issuance

A gateway subcomponent, isolated because printer integration is the flakiest hardware dependency and must never block evidence creation ordering: manifest → store → receipt → next scan.

**Responsibilities:** render the `MA1` payload (§3.5) as QR + 80-bit human-readable line; drive ESC/POS thermal printers (primary target — ubiquitous, cheap, driverless over USB/serial) and a display fallback (venue screen showing the QR for candidate phone capture); log `receipt.issued` with the manifest hash to the audit chain. A failed print is an audit event and an operator alert — the proposal makes receipts conditional on session logistics, but the *default* is issue-always, and a venue that disables receipts loses the distributed-witness control and should know it (recorded in deployment config, visible to auditors via the audit chain).

**Invariants upheld:** M-3 (full hash in QR, ≥80-bit print floor); M-5 (salt delivery vehicle); Inv-7 (a receipt identifies no one to a third party).

### 4.5 CLI Verifier — `ma-verify` (the product)

Everything else exists so this binary can say something trustworthy. It is fully offline-capable given a PVM bundle and evidence input (anchor verification of TSA tokens needs no network; `witnessed-root` verification reports which witness checks were performed offline vs. skipped).

**Inputs:** PVM bundle (pinned, §3.8); then any of: evidence package directory, batch record + proofs, receipt payload/photo, or a whole published batch tree.

**Check list** — complete mapping of the proposal's *MA Verifier* section to Phase 1 (checks marked ▸ are Phase 2+ but their *verdict vocabulary* is reserved now, Inv-6):

| # | Check (proposal) | Phase 1 behavior |
|---|---|---|
| 1 | Image integrity vs. manifest | recompute page hashes over stored bytes, compare, check declared order and `page_count` |
| 2 | Signature validity | if `.sig` present: verify against PVM key entry, honoring validity/revocation windows; absence in Phase 1 is reported as `unsigned (anchored)`, not a failure (H-2, Inv-4) |
| 3 | Anchor inclusion | manifest → leaf (§5.2) → inclusion proof → batch root → batch record hash → anchor proof verify (per class) |
| 4 | Anchor uniqueness | all known batch records for `session_id`: >1 valid ⇒ `anchor-conflict`, earliest wins (Inv-5); anchor endpoint must match PVM pre-declared endpoints |
| 5 | Session-registry completeness | for every session record in PVM with `batch_deadline` past: anchored batch exists ⇒ ok; else `session-without-batch` (whole-batch suppression, C-1) |
| 6 | Scan-to-anchor latency | `anchor_time − scan_time` vs. effective declared max (session override else PVM); also `scan_time` within scheduled window (A-4); exceed ⇒ `anchored-out-of-window` (M-1) |
| 7 | Roster completeness | both directions: every roster-tree commitment has exactly one current manifest in the batch; every batch manifest's commitment is in the roster tree; missing ⇒ `roster-entry-without-package` (= first-class failure, on par with hash mismatch); extra ⇒ `package-without-roster-entry` |
| 8 | Document completeness | all declared pages present, in order — folded into check 1 |
| 9 | Result-to-image binding ▸ | Phase 2 (verdict strings `result-not-reproducible`, `bound-via-unverifiable-derivation` reserved) |
| 10 | Result uniqueness ▸ | Phase 2 |
| 11 | Derivation chain ▸ | Phase 2 |
| 12 | Supersession chain | Phase 1 minimal: `supersedes` targets exist and are anchored; chains are acyclic; two current packages for one commitment ⇒ `duplicate-package`; supersession *rate* per operator/scanner reported as an anomaly signal (proposal → Handling Legitimate Rescans); window/authorizer checks ▸ Phase 2 (`superseded-out-of-window` reserved) |
| 13 | Candidate receipt | parse `MA1`; find `m` in the batch (hash list or entries); verify inclusion proof to the anchored root; optionally, with candidate-entered ID + `r` salt, recompute the commitment and prove roster membership |
| 14 | Audit-log chain integrity | recompute the chain; head at `head_seq` must equal the anchored `audit.head` |
| 15 | Timestamp validity | TSA token cryptographic validity, cert chain, time plausibility vs. session window |
| 16 | Chain of custody | Phase 1 = the composition of 14 + 12 + 6 rendered as a custody timeline in the report; richer custody events accrue in later phases |

**Verdict model (Inv-6).** Output is structured (JSON + human report), two layers, never a boolean:

- **Per document:** `current` | `superseded` | `superseded-out-of-window`▸ | `duplicate-package` | `tampered` (hash/order/count mismatch) | `missing` (roster entry, no package) | `unsupported-schema`; orthogonal qualifiers: `unsigned (anchored)`, `bound-via-unverifiable-derivation`▸, `result-not-reproducible`▸.
- **Per batch/session:** `anchored` | `anchored-out-of-window` | `anchor-conflict` | `session-without-batch` | `audit-chain-broken`; qualifier: `anchor-class = tsa-rfc3161|witnessed-root|transparency-log` always displayed (H-1 — class is weighed, not hidden).

The CLI exposes no flag that collapses these to pass/fail. Exit codes encode only "ran / input malformed / found ≥1 integrity failure" for scripting; the verdict detail is the JSON.

**Reproducible builds (M-6):** see §5.5. The PVM bundle carries expected verifier artifact hashes; `ma-verify self` prints its own hash for cross-checking, with the documented caveat that a lying binary can lie about itself — the real control is rebuilding from source, which the build system makes bit-exact.

### 4.6 Audit chain

A library embedded in `ma-gateway` (and `ma-registry` for RA-side events), not a separate daemon — a separate service would add an availability dependency without adding trust (the chain is operator-held either way; its integrity comes from the anchored head, M-7, Inv-4).

Append-only JSONL per §3.10; fsync per entry; `seq`/`prev` verified on process start (a torn tail halts the gateway rather than forking the chain — Inv-1). The head is captured into every sealed batch record; entries after the last seal are covered by the *next* anchor, so the unanchored audit tail is bounded by the batch cadence — the same M-1 window logic, and reported the same way.

---

## 5. Key decisions (handoff §5.3)

### 5.1 Decision 1 — Scanner ingestion interface

**Decision: watch folder as the primary interface, plus an explicit `ma-gateway ingest` CLI. No TWAIN/SANE driver integration in Phase 1.**

- *Alternatives:* (a) TWAIN/SANE integration — rejected for Phase 1: driver matrices are large, quality varies, and it violates the spirit of Vendor Independence (the proposal explicitly does not mandate an ingestion interface); (b) network endpoint the scanner pushes to (FTP/SMB/HTTP) — effectively a remote watch folder; supported by pointing the watcher at the receiving directory, but the *recommended* topology keeps that directory on the gateway host.
- *How it works:* production scan stations universally support "scan to folder." The gateway watches a spool directory (fsnotify + polling fallback), applies a stability rule (file size unchanged across two intervals, or scanner-side atomic rename), then immediately hashes, manifests, receipts, and moves the bytes into the append-only store. Target: < 5 s from file-complete to receipt.
- *M-1 analysis (scan-to-anchor window):* the watch folder adds a **pre-registration dwell** — bytes sit in the spool, cryptographically unconstrained, before hashing. This dwell is *inside* the window the trust model already declares uncontrolled (before registration), so the interface adds no new class of exposure, but it must be kept short and measurable: (1) spool on the gateway host's local disk, not a network share where dwell is invisible; (2) the gateway audits `scan.completed` with ingest latency, and abnormal spool dwell (files older than a threshold at pickup) is an audit event; (3) `scan_time` is set by the gateway at hash time — the honest, measurable point — never taken from scanner metadata (which is trivially forgeable and would corrupt the latency check); (4) the receipt is issued at that same moment, giving the candidate-held hash its earliest possible fixation. The declared `max_scan_to_anchor` then governs hash→anchor; the residual scanner→hash seconds are procedural territory (dual-operator station), exactly as the Trust Model draws the boundary.
- Client capture (web/mobile) is Phase 4; the manifest's `capture.class` field already distinguishes it, so no schema change will be needed.

### 5.2 Decision 2 — Merkle tree construction and proof format

**Decision: RFC 6962 tree construction, exactly** — `leaf_hash = H(0x00 ‖ leaf_data)`, `node = H(0x01 ‖ left ‖ right)`, unbalanced trees split at the largest power of two, `H = SHA-256` in Phase 1 (`tree.style = "rfc6962-sha256"`; a future style string is the L-1 succession hook).

- *Why:* (1) domain separation kills leaf/node second-preimage confusion; (2) it is exactly what Rekor/CT implement, so Phase 3 can anchor the *same* roots into a transparency log and reuse audited open-source tree code (Trillian/sunlight lineage) instead of inventing crypto (proposal → Build on Standards); (3) audit-path proofs are compact (⌈log₂ n⌉ hashes).
- *Alternatives:* plain unkeyed binary tree (no domain separation — rejected, known weakness); hash list only (no per-document proof without the full batch — rejected, breaks receipt verification at scale and would force publishing more than necessary); sparse Merkle tree keyed by commitment (elegant for roster lookups but nonstandard in this role and needlessly clever — rejected).
- *Leaf definition:* `leaf_data` = JCS bytes of the batch entry object `{"index":i,"type":"manifest","hash":"sha256:…"}`. Typed leaves let Phase 2 add `result` and `exception` entries to the same tree without ambiguity. Entry order = scan order (deterministic given the batch record; also encodes useful forensic sequence).
- *Two trees, one style:* the batch tree (scan-ordered, typed leaves) and the roster tree (§3.4; sorted raw commitment values as leaf_data). Same `rfc6962-sha256` construction everywhere; one implementation, one test-vector suite.
- *Proof format:* §3.7 — leaf index, tree size, path bottom-up, verified by the standard RFC 6962 audit-path algorithm.

### 5.3 Decision 3 — Evidence package storage layout and access control

**Decision: content-addressed filesystem store with a session-structured reference layer; page images segregated under a restricted tier. Object storage is a Phase 2+ backend behind the same layout, not a Phase 1 requirement.**

```text
ma-store/
├── public/                             # tier P — publishable, mirrored to PVM publisher
│   └── sessions/<session_id>/
│       ├── session_record.json + anchor_proof.json     (from RA)
│       ├── batch_record.json + anchor_proof.json
│       └── hashlist.json
├── evidence/                           # tier E — auditors under agreement; NO page pixels
│   └── sessions/<session_id>/packages/<manifest_hash_hex>/
│       ├── manifest.json  [manifest.sig]
│       └── inclusion_proof.json
├── images/                             # tier I — restricted: pages of scripts
│   └── objects/sha256/<aa>/<bb>/<full_hash>            # content-addressed page bytes
├── restricted/                         # tier R — operator-internal
│   └── sessions/<session_id>/{session_pack/, receipts_log}
└── audit/<chain_id>/audit.jsonl        # tier E (head is tier P via batch records)
```

- *Why filesystem first:* Phase 1 targets venues with modest IT; a directory tree is auditable with standard forensic tooling and imposes no service dependency. *Alternative:* object store (S3-compatible, versioning + object lock) — better WORM enforcement, and the layout above maps 1:1 to keys, so it becomes a drop-in backend; rejected as a Phase 1 *requirement* because it would exclude exactly the deployments Phase 1 serves.
- *Content addressing* of page bytes: the path is derived from the hash, so post-hash rewriting is structurally awkward and dedup across rescans is free. The gateway store API is **create-only** (Inv-1): no code path writes to an existing object path; supersession adds a new manifest, never touches old objects.
- *Access control is a first-class concern (Privacy by Design — handwriting is a biometric):* tier I lives on a separate mount with OS enforcement (dedicated service account; operators get no read access — only the gateway process writes, only an explicit `ma-gateway export` — dual-person procedure, audited as `evidence.accessed`/`export.requested` — reads). Full-disk or per-mount encryption at rest is required deployment guidance for tier I. **Only hashes are ever published, never pixels:** nothing in tier P or E contains image bytes, and the schemas make that structural (batch records, hash lists, and inclusion proofs contain no page data).
- *Retention:* append-only until the deployment's legally mandated retention horizon; deletion after that horizon is a physical-custody procedure outside MA's write paths, and the anchored hashes remain valid evidence of what existed (the proposal's public-log permanence stance).

### 5.4 Decision 4 — Optional deployment signing key (H-2)

**Decision: Ed25519, generated in a documented two-person key ceremony, stored on a hardware token (FIPS-mode YubiKey/PIV or equivalent, PKCS#11) held by a named custodian *outside* the scanning room; fingerprint and public key published in the PVM bundle before first use.**

- *What it is for — and not for:* attribution only. It makes a parallel conflicting package **attributable**, complementing first-anchor-wins; it is never load-bearing for detection (Inv-4). The verifier treats `unsigned (anchored)` as a reported property, not a failure (§4.5 check 2).
- *Custody rationale:* the proposal is explicit that an operator-held key proves nothing against the operator (Key custody). Phase 1 cannot mandate an external key holder (that maturity arrives Phase 2/3), so the realistic best is: hardware-bound (non-exportable), custodian role ≠ scanner operator, ceremony minuted, fingerprint externally published so *substituting* the key is detectable via the PVM chain (§3.8 rotation semantics).
- *Fallback:* deployments without hardware tokens may use an OS-keychain-protected file key; the PVM key entry then records `"custody": "software"`, and the verifier surfaces it (Inv-6 — weaker evidence is labeled, not hidden). *Alternative rejected:* per-scanner keys in Phase 1 — multiplies ceremony/custody burden for no trust gain while the operator controls all of them anyway.
- Ed25519 over ECDSA-P256: deterministic signatures (no nonce-reuse foot-gun), small keys, first-class in the Go/Sigstore ecosystem. Algorithm is a field (`keys[].alg`), so this too is agile.

### 5.5 Decision 5 — Language and runtime

**Decision: Go (single module, ≥ latest LTS-ish stable at implementation start), CGO disabled, static binaries for `ma-gateway`, `ma-registry`, `ma-verify`.**

- *Against the constraints:* **CLI-first** — Go's deployment story is a single static binary per OS/arch, ideal for an RA workstation, a venue gateway PC, and a verifier download alike. **Reproducible builds required (M-6)** — Go is the strongest mainstream option: `CGO_ENABLED=0 go build -trimpath` with a pinned toolchain and module sums is bit-reproducible in practice, and the ecosystem's flagship reproducibility projects (Sigstore, Go's own oss-rebuild coverage) prove the path; CI rebuilds on independent infrastructure and compares artifact hashes published in the PVM. **Long-term open-source maintenance** — large contributor pool, boring-and-stable stdlib crypto (`crypto/sha256`, `crypto/ed25519`, `crypto/x509`), and the exact libraries this design leans on are maintained in Go: RFC 3161 (digitorus/timestamp lineage), RFC 6962 trees (transparency-dev/merkle), Sigstore clients for Phase 3, JCS implementations.
- *Alternatives:* **Rust** — memory safety and excellent CLIs, but bit-reproducibility takes more build-system work, and the Phase 3 gravitational pull (Sigstore/Rekor, Trillian) is Go-native; viable, second choice. **Python** — fastest to write, but reproducible single-file distribution is genuinely hard and supply-chain surface is large; unacceptable for the verifier, and splitting languages between verifier and gateway doubles the maintained crypto surface. Verifier and gateway share one hashing/tree/JCS library in one language so a divergence bug cannot silently split "what was anchored" from "what verifies."

### 5.6 Decision 6 — Offline operation and anchor queueing (M-1)

**Decision: a durable local anchor spool with unlimited retry inside the declared window, plus a *detached anchoring* path: because anchoring needs only the sealed batch record (a few KB — no images, no manifests), submission can run on any connected machine, physically couriered if necessary.**

- *Mechanics:* `batch.sealed` writes the batch record and enqueues `{session_id, batch_record_hash}` in a WAL-backed spool. The anchor worker retries with backoff while connectivity is absent. `ma anchor submit batch_record.json` runs standalone: an offline venue copies the sealed record to a phone/USB stick, and any connected machine (venue office, RA, even the PVM publisher) performs the TSA call and returns the token, which the gateway ingests as `anchor.confirmed`. The record is self-contained and its hash is what is anchored, so the submitting machine is untrusted — it can delay or refuse (detectable: `anchored-out-of-window` / `session-without-batch`) but cannot alter what is anchored (Inv-4).
- *The window is declared per session, honestly:* an offline venue's `ma/session/v1` record carries a realistic `max_scan_to_anchor` (e.g. `PT24H` for a courier plan) **fixed pre-session by the RA** (Inv-8) — never widened after the fact. The verifier applies the session's declared window; a wide window is visible in the anchored registry for anyone to weigh (Inv-6), which is exactly the M-1 design: the window is a controlled, declared quantity, not a hidden one.
- *Fallback class for extended outages:* if the TSA is unreachable through the window, the `witnessed-root` adapter path (gazette/archiver publication of the batch record hash) can meet the deadline via literally any out-of-band channel — 64 hex characters read over a phone line is a valid submission. Anchor class is recorded per batch, so a fallback-anchored batch is permanently labeled as such (H-1).
- *What is never allowed:* processing (OMR/grading) before anchor confirmation for the batch (Inv-2). The gateway export command that hands images to processing refuses until `anchor.confirmed` — offline venues therefore anchor before they process, or they wait. This ordering is the invariant; the spool only changes *where* the anchoring call runs.
- Receipts are unaffected by connectivity: they are issued at scan time from local data (§3.5), which is precisely why they reach back before the anchor (Trust Model → What MA proves).

---

## 6. Verification data flow (end to end)

```text
PRE-SESSION (RA)                     SESSION (Operator)                POST-SESSION
────────────────                     ──────────────────                ────────────
roster.csv                           scanner → spool dir
  │ ma-registry                        │ stability check
  ▼                                    ▼
salts + commitments                  hash pages (final bytes)          seal batch
roster tree (6962)                     │                                 │ 6962 root
  │                                    ▼                                 ▼
session record ──anchor──▶ TSA       manifest (ma/manifest/v1)         batch record ──anchor──▶ TSA
  │                                    │        │                        │            (or courier /
  ▼                                    ▼        ▼                        ▼             witnessed root)
session pack ────────────▶ gateway   store    receipt (QR: hash+salt)  hashlist + inclusion proofs
                                     (tier I/E) │                        │
                                                ▼                        ▼
                                             candidate               PVM publisher (tier P mirror)

VERIFY (anyone): pin PVM → check session registry ↔ batches (both directions)
→ anchor proof + uniqueness → latency → roster ↔ packages (both directions)
→ page hashes/order/count → supersession chains → audit head → verdicts (multi-valued)
```

---

## 7. Threat analysis (companion section recommended by the security review)

### 7.1 Adversaries

| ID | Adversary | Capabilities | Primary goal |
|----|-----------|--------------|--------------|
| ADV-1 | **Operating organization** (the core threat, per proposal) | Full control of gateway hosts, storage, network, operator staff, its own keys; can attempt parallel anchoring | Alter/substitute/suppress evidence or (Phase 2+) results without detection |
| ADV-2 | Insider scanner operator (lone) | One station during a session; physical sheet access pre-scan; rescan privilege | Targeted alteration/omission for specific candidates |
| ADV-3 | Registration authority (rogue or colluding) | Roster contents pre-anchor; session scheduling | Drop/insert candidates before commitment; rig windows |
| ADV-4 | Candidate / coached candidate | Own sheet pre-scan; own receipt; false dispute claims | Fraudulent "tampering" claims; pre-scan sheet substitution |
| ADV-5 | External network attacker | Network between components; public channels | Corrupt records in transit; deface published material |
| ADV-6 | Anchor provider (TSA / publication channel) | Can refuse service; backdate/misdate tokens; (log: split view) | Enable operator fraud or deny anchoring |
| ADV-7 | Verifier-supply-chain attacker | Distribution of verifier binaries / PVM bundles | Ship a verifier or trust inputs that lie |

### 7.2 Attack tree

Goal: **exam evidence or its absence goes undetected** — each leaf mapped to control → invariant → verifier verdict. ⊘ marks residual risks Phase 1 accepts explicitly.

```text
G. Undetected manipulation of examination evidence
├── G1. Modify content after anchoring                       [ADV-1,2,5]
│   ├── G1.1 Rewrite page bytes in store
│   │     → page hash mismatch vs anchored root (Inv-3,4) ⇒ "tampered"
│   ├── G1.2 Reorder / remove pages
│   │     → ordered hashes + page_count in manifest ⇒ "tampered"
│   └── G1.3 Rewrite manifest to match new bytes
│         → manifest hash is the anchored leaf (Inv-2,4) ⇒ inclusion proof fails ⇒ "tampered"
├── G2. Substitute rather than modify                        [ADV-1]
│   ├── G2.1 Second package for same candidate, same batch
│   │     → one-current rule per commitment ⇒ "duplicate-package" (H-2)
│   ├── G2.2 Parallel batch record for same session (anchor equivocation)
│   │     → single pre-declared endpoint + first-anchor-wins (Inv-5, H-1)
│   │       + receipts as distributed witnesses ⇒ "anchor-conflict"
│   │     ⊘ residual: TSA cannot prove uniqueness; a conflicting record shown
│   │       ONLY to a victim who never sees the genuine one — mitigated by
│   │       published hash lists + PVM-pinned endpoints; eliminated Phase 3 (log class)
│   └── G2.3 Doctored rescan via supersession                 [ADV-2]
│         → Phase 1: supersession anchored + visible, rate anomalies (M-9)
│         ⊘ residual until Phase 2 authorizer signature + window (M-4);
│           verdict "superseded" is always distinct from "current" (Inv-6)
├── G3. Omission                                             [ADV-1,3]
│   ├── G3.1 Drop candidate at scan time
│   │     → pre-anchored roster commitment (C-1, Inv-8) ⇒ "roster-entry-without-package"
│   ├── G3.2 Drop candidate from roster pre-anchor            [ADV-3, or ADV-1+3 collusion]
│   │     ⊘ outside cryptographic boundary; controls: RA organizational separation,
│   │       candidate-facing registration confirmation (procedural), RA audit
│   ├── G3.3 Suppress an entire batch
│   │     → session registry two-direction check (C-1) ⇒ "session-without-batch"
│   └── G3.4 Never anchor, claim "still pending"
│         → declared max_scan_to_anchor + batch_deadline (M-1) ⇒ "anchored-out-of-window" / "session-without-batch"
├── G4. Attack the window before registration                [ADV-1,2,4]
│   └── G4.1 Alter/substitute physical sheet or spool file pre-hash
│         ⊘ explicitly outside the boundary (Trust Model → What MA cannot prove);
│           controls: procedural (dual operator, CCTV), spool dwell auditing (§5.1),
│           receipt fixes hash at earliest moment; window measured & declared (M-1)
├── G5. Attack the trust/verification path                   [ADV-1,5,7]
│   ├── G5.1 Distribute lying verifier
│   │     → reproducible builds + artifact hashes in PVM (M-6)
│   ├── G5.2 Substitute PVM bundle / hide key rotation
│   │     → operator-independent publisher, hash-chained anchored versions,
│   │       verifier pinning (§3.8) ⇒ divergent-chain detection
│   └── G5.3 Rewrite audit history
│         → hash chain + head in every batch anchor (M-7, Inv-1) ⇒ "audit-chain-broken"
├── G6. Attack anchors themselves                            [ADV-6]
│   ├── G6.1 TSA backdates/misdates tokens
│   │     ⊘ trust in TSA cert + its own audit regime; cross-check vs session
│   │       window (A-4); class recorded ⇒ verifier weighs (H-1)
│   ├── G6.2 Witnessed-root split view
│   │     → ≥2 independent witnesses / issue-numbered channel required (H-1)
│   └── G6.3 Anchor denial of service
│         → detached anchoring + fallback class (§5.6); failure is loud
│           ("anchored-out-of-window"), never silent
├── G7. Attack candidates / receipts                          [ADV-1,4]
│   ├── G7.1 Grind a doctored manifest to a truncated receipt hash
│   │     → full 256-bit hash in QR; 80-bit print floor (M-3)
│   ├── G7.2 Candidate forges a receipt to claim tampering
│   │     → receipt only *claims*; verification is against the anchored batch —
│   │       a forged receipt simply fails inclusion ⇒ dispute resolved by evidence
│   └── G7.3 Withhold receipts / salts
│         → receipt issuance audited; salt on receipt is mandatory guidance (M-5);
│           disabled receipts recorded and visible to auditors (§4.4)
└── G8. Deanonymize candidates from published artifacts       [ADV-5, anyone]
    ├── G8.1 Enumerate small candidate-ID space
    │     → 128-bit per-record salts / per-session HMAC keys (M-5, Inv-7)
    └── G8.2 Leak via published content
          → structural: tiers P/E contain no pixels, no IDs, no salts (§5.3, Inv-7)
```

### 7.3 What Phase 1 explicitly does not defend (honest residuals)

Pre-registration physical fraud (G4.1), RA+operator collusion (G3.2), two-insider supersession fraud pending Phase 2 dual authorization (G2.3), TSA misbehavior within its own trust envelope (G6.1), and TSA-class equivocation shown only to isolated victims (G2.2 residual). Every one of these is either stated in the proposal's Trust Model as out of scope, or scheduled (M-4 → Phase 2; anchor class upgrade → Phase 3). None is silently absorbed: each has a verdict, a recorded property, or documented procedural guidance.

---

## 8. Design-for-later

### 8.1 L-1: long-term validity — re-anchoring and hash succession

Not solved in v0.6; the schemas above satisfy the stated minimum (algorithm agility everywhere) and leave these seams:

- **Re-anchoring records.** `ma/anchor-proof/v1.reanchors[]` accepts future `ma/reanchor/v1` records: `{original_anchored_hash, reanchor_time, reason: "tsa-cert-expiry" | "algorithm-succession" | "anchor-upgrade", new_anchor: <anchor-proof>}`. Because it is an *append* to the proof, not a mutation of the batch record, Inv-1 holds and old proofs remain evaluable. The intended Phase 3 move — re-anchoring all historical batch roots into Rekor — is exactly `reason: "anchor-upgrade"` entries, and needs no data migration because the trees are already RFC 6962 (§5.2).
- **TSA token aging:** verify-at-time semantics — the verifier evaluates the TSA cert chain as of `genTime`, and reports `anchor-evidence-aging` (a qualifier, not a failure — Inv-6) once the chain is expired *and* no re-anchor record exists. PVM retains superseded TSA CA certs forever (append-only bundle, §3.8) so old tokens stay checkable.
- **Hash succession:** when SHA-256 must be succeeded, (1) new records simply use new `hash_alg` / `"sha3-256:…"` values — the prefixed-hash convention means no field changes; (2) existing evidence gains `ma/reanchor/v1` records anchoring `H_new(old_record_bytes)` — valid because the original *bytes* are retained (Inv-1), so successor-algorithm hashes can always be recomputed from primary evidence, not from old hashes; (3) `tree.style` strings version the Merkle construction independently. The one rule implementations must obey now: **never discard the exact anchored bytes of any record** — succession re-hashes bytes, not hashes.

### 8.2 C2PA interoperability (Phase 4) — keeping the manifest out of the corner

- MA page hashes are over **exact stored bytes** (Inv-3), which matches C2PA hard-binding assertions (`c2pa.hash.data` over asset bytes) — so a C2PA-capable scanner's claim and MA's page hash can bind to the *same bytes* with no reconciliation layer. Do not ever introduce "semantic" or format-normalizing hashing; it would break this equivalence.
- `manifest.ext.c2pa` is the reserved seam: a future `{active_manifest_hash, claim_generator}` pairing lets an MA manifest commit to the C2PA manifest embedded in the page file — the C2PA store travels *inside* the hashed page bytes, so it is already covered by the anchor today; the ext field only adds structured cross-reference.
- MA concepts map onto C2PA vocabulary without forcing MA into JUMBF: MA manifest ≈ claim; derivation record (Phase 2) ≈ ingredient with `parentOf` relationship; capture class + device attestation ≈ claim generator info + CAWG attestation assertions. The mapping stays a *projection* (an export tool), not a storage format change: JSON+JCS remains MA's native encoding, chosen for verifier reimplementability.
- One constraint honored now: MA manifests never embed image bytes or thumbnails (also Inv-7), so the C2PA projection can be generated for external consumers without privacy review of the core format.

---

## 9. Testing strategy and operational rollback

**Testing (the verifier is the product; the test corpus is its warranty):**

1. **Golden vectors:** JCS canonicalization, prefixed-hash encoding, RFC 6962 trees (cross-checked against transparency-dev/merkle outputs), TSA token verification against public TSAs and a local test TSA — published in-repo so independent reimplementations can conform.
2. **Adversarial corpus, generated from the attack tree:** every leaf in §7.2 that has a verdict gets at least one fixture (a deliberately corrupted store/batch/proof) and a CI assertion that `ma-verify` emits *exactly* the mapped verdict — this pins Inv-6 in CI: a change that collapses or mislabels a verdict fails the build.
3. **Round-trip/property tests:** random rosters/batches → seal → verify = all-current; then property-based mutation (flip a byte anywhere in store or records) must change at least one verdict.
4. **Reproducibility CI:** two independent builders (different OS images) must produce identical artifact hashes per release; the PVM `verifier_builds` entry is generated from that agreement, never hand-edited (M-6).
5. **Offline soak:** simulated venue with no connectivity through seal + courier-file anchoring inside a `PT24H` window (§5.6), including a forced-fallback (`witnessed-root`) drill.

**Rollback (constrained by Inv-1 — there is no destructive rollback in this system):**

- **Evidence is never rolled back.** Operational errors during a session are corrected *forward*: bad scan → supersession; bad batch sealed with wrong roster reference → the batch is anchored as-is and a corrected batch cannot reuse the `session_id` (Inv-5) — the RA issues a new session record (a visible, anchored event) and the defect is documented. Painful by design: silent correction is the attack.
- **Software rollback** (gateway/verifier version) is always safe because records are self-describing (`schema`, versions in `gateway.software`) and old binaries reject newer majors loudly (`unsupported-schema`).
- **Trust-material rollback does not exist:** a bad PVM bundle version is superseded by the next version (append-only chain, §3.8); verifiers that pinned the bad version still converge because the chain is linear.
- **Anchor-provider failure mid-session:** the pre-declared fallback class path (§5.6) is the continuity plan; switching *primary* anchor class or endpoint is a PVM bundle event, pre-declared before it is used, never retroactive.

---

## 10. Traceability index

| Architecture element | Source |
|---|---|
| Session registry CLI, pre-session anchoring, session pack | C-1, Inv-8, Trust Model → Completeness |
| Anchor adapter classes, class recorded per batch, single endpoint, first-anchor-wins | H-1, Inv-4, Inv-5, Trust Model → Anchor strength |
| `duplicate-package` verdict, optional deployment key | H-2, Evidence Package |
| Reserved derivation/result verdicts and `type`d tree leaves | H-3, H-4 (Phase 2 seams) |
| `scan_time` semantics, spool dwell auditing, per-session declared window, offline courier path | M-1, §5.1, §5.6 |
| One-current-per-commitment rule (result form reserved) | M-2 |
| `MA1` full-hash QR, 80-bit print floor, hash lists, receipt check in CLI | M-3, Candidate Receipts |
| `supersedes.authorized_by` seam, out-of-window verdict reserved | M-4, M-9 |
| Salted-hash commitments, RA-generated salts on receipts, per-session HMAC option | M-5, Privacy by Design, Inv-7 |
| PVM bundle: independent publisher, anchored hash-chained versions, pinning, reproducible builds | M-6 |
| Audit chain JSONL, head in every batch record | M-7, Inv-1 |
| Grader keys | M-8 — Phase 3, no Phase 1 surface; schema `keys[].role` accommodates |
| Prefixed hashes, `hash_alg`, `tree.style`, `reanchors[]`, byte retention rule | L-1, §8.1 |
| (session, venue) batch definition | L-2, A-1 |
| Threat section | handoff §6 (review recommendation) |
