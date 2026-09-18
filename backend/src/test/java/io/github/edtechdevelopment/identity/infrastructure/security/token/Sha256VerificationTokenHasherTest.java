package io.github.edtechdevelopment.identity.infrastructure.security.token;

import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Sha256VerificationTokenHasherTest {

    private final Sha256VerificationTokenHasher tokenHasher = new Sha256VerificationTokenHasher();

    @Test
    void hashesTokenAsLowercaseSha256Hex() {
        VerificationTokenHash tokenHash = tokenHasher.hash("abc");

        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                tokenHash.value()
        );
    }

    @Test
    void producesStableHashForLookup() {
        VerificationTokenHash firstHash = tokenHasher.hash("verification-token");
        VerificationTokenHash secondHash = tokenHasher.hash("verification-token");
        VerificationTokenHash differentHash = tokenHasher.hash("another-token");

        assertEquals(firstHash, secondHash);
        assertNotEquals(firstHash, differentHash);
    }
}
