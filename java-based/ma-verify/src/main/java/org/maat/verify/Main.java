package org.maat.verify;

import org.maat.core.CanonicalJson;
import org.maat.core.anchor.Rfc3161Anchor;
import org.maat.core.model.Receipt;
import org.maat.core.verify.VerificationReport;
import org.maat.core.verify.Verifier;

import java.nio.file.Path;

/**
 * The standalone verifier — what an independent third party runs against a
 * copy of public/ + evidence/ only. It shares the verdict engine with the web
 * app's verify page but ships as its own jar precisely so the demo can say:
 * "this binary never touched the operator's application."
 *
 * Exit code semantics: 0 = verification RAN, non-zero = could not run.
 * Verdicts are the output, never the exit code (invariant 6: multi-valued,
 * not pass/fail).
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || ("receipt".equals(args[0]) && args.length < 5)) {
            System.err.println("""
                usage:
                  ma-verify batch   <publicDir> <evidenceDir> <sessionId>
                  ma-verify receipt <publicDir> <evidenceDir> <MA1|...payload> <candidateId>
                """);
            System.exit(2);
        }
        // TSA cert pinning config is the capstone stand-in for the PVM bundle (M-6): TODO(Dev A)
        Verifier verifier = new Verifier(new Rfc3161Anchor("https://freetsa.org/tsr"));
        Path publicDir = Path.of(args[1]);
        Path evidenceDir = Path.of(args[2]);

        VerificationReport report = switch (args[0]) {
            case "batch" -> verifier.verifyBatch(publicDir, evidenceDir, args[3]);
            case "receipt" -> verifier.verifyReceipt(publicDir, evidenceDir,
                    Receipt.decode(args[3]), args[4]);
            default -> throw new IllegalArgumentException("unknown command: " + args[0]);
        };
        System.out.println(CanonicalJson.mapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(report));
    }
}
