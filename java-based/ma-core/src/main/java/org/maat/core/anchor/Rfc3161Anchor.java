package org.maat.core.anchor;

import org.maat.core.Hashes;
import org.maat.core.model.AnchorProof;

/**
 * RFC 3161 TSA adapter — THE day-1 spike (SPRINT_PLAN.md, owner: Dev B).
 * This is the only hard external protocol in the whole build, which is why it
 * gets the first hours: if a public TSA round-trip works by day-1 lunch, the
 * rest of the sprint is plain Java.
 *
 * Implementation sketch (BouncyCastle bcpkix, already on the classpath):
 *
 *   anchor(hash):
 *     TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
 *     gen.setCertReq(true);                                  // ask TSA to include its cert
 *     TimeStampRequest req = gen.generate(TSPAlgorithms.SHA256,
 *                                         Hashes.rawBytes(hash), nonce);
 *     POST req.getEncoded() to tsaUrl,
 *          Content-Type: application/timestamp-query          (plain HttpClient)
 *     TimeStampResponse resp = new TimeStampResponse(bodyBytes);
 *     resp.validate(req);                                     // nonce + imprint check
 *     token = resp.getTimeStampToken();  → base64 of token.getEncoded()
 *     anchored_at = token.getTimeStampInfo().getGenTime()
 *
 *   verify(proof, hash):
 *     TimeStampToken token = new TimeStampToken(new CMSSignedData(decode(token_b64)));
 *     1. token.getTimeStampInfo().getMessageImprintDigest()
 *          must equal Hashes.rawBytes(hash)                   // the right bytes are anchored
 *     2. token.validate(new JcaSimpleSignerInfoVerifierBuilder()
 *          .build(tsaCert))                                   // signature chains to pinned cert
 *     tsaCert comes from ma-verify's pinned config (capstone stand-in for the
 *     PVM bundle, arch §3.8 — say so in the report, finding M-6 unimplemented).
 *
 * Free public TSAs for the demo: https://freetsa.org/tsr , DigiCert.
 * Definition of done: remove @Disabled from Rfc3161AnchorLiveTest and both
 * tests pass, plus VerifierContractTest's anchored-path tests go green.
 */
public final class Rfc3161Anchor implements AnchorAdapter {

    private final String tsaUrl;

    public Rfc3161Anchor(String tsaUrl) {
        this.tsaUrl = tsaUrl;
    }

    @Override
    public AnchorProof anchor(String hashString) throws AnchorException {
        Hashes.rawBytes(hashString); // validate input early
        throw new AnchorException("TODO(Dev B, day 1 AM): RFC 3161 request against " + tsaUrl
                + " — see class javadoc for the BouncyCastle sketch");
    }

    @Override
    public boolean verify(AnchorProof proof, String hashString) throws AnchorException {
        throw new AnchorException("TODO(Dev B, day 1 PM): token verification — see class javadoc");
    }
}
