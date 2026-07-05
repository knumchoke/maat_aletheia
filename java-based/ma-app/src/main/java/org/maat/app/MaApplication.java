package org.maat.app;

import org.maat.core.anchor.AnchorAdapter;
import org.maat.core.anchor.FakeAnchor;
import org.maat.core.anchor.Rfc3161Anchor;
import org.maat.core.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@SpringBootApplication
public class MaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaApplication.class, args);
    }

    @Bean
    public Store store(@Value("${ma.workdir:./workdir}") String workdir) {
        return new Store(Path.of(workdir));
    }

    /**
     * anchor mode: `fake` until Dev B lands the TSA adapter, then flip
     * application.properties to `rfc3161`. The verify page will (correctly)
     * report fake-anchored records as UNANCHORED — that is the system being
     * honest, not a bug.
     */
    @Bean
    public AnchorAdapter anchorAdapter(@Value("${ma.anchor.mode:fake}") String mode,
                                       @Value("${ma.anchor.tsa-url:https://freetsa.org/tsr}") String tsaUrl) {
        return switch (mode) {
            case "rfc3161" -> new Rfc3161Anchor(tsaUrl);
            case "fake" -> new FakeAnchor();
            default -> throw new IllegalArgumentException("ma.anchor.mode must be fake|rfc3161, got " + mode);
        };
    }
}
