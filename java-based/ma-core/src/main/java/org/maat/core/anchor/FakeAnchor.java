package org.maat.core.anchor;

import org.maat.core.model.AnchorProof;

import java.time.Instant;

/**
 * DEMO-ONLY anchor for offline development. Provides ZERO tamper evidence:
 * the operator mints it, so the operator can re-mint it (exactly the attack
 * MA exists to detect). The verifier MUST report fake-anchored records as
 * UNANCHORED — there is a contract test pinning that behaviour.
 */
public final class FakeAnchor implements AnchorAdapter {

    @Override
    public AnchorProof anchor(String hashString) {
        return new AnchorProof(AnchorProof.CLASS_FAKE, hashString, null, null, Instant.now().toString());
    }

    @Override
    public boolean verify(AnchorProof proof, String hashString) {
        // A fake anchor never verifies as an anchor. Not "verifies weakly" — never.
        return false;
    }
}
