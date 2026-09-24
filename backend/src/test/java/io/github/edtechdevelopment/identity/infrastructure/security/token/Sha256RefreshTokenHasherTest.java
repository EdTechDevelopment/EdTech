package io.github.edtechdevelopment.identity.infrastructure.security.token;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Sha256RefreshTokenHasherTest {

    private final Sha256RefreshTokenHasher tokenHasher = new Sha256RefreshTokenHasher();

    @Test
    void hashesTokenAsLowercaseSha256Hex() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                tokenHasher.hash("abc")
        );
    }

    @Test
    void producesStableHashForLookup() {
        String firstHash = tokenHasher.hash("refresh-token");
        String secondHash = tokenHasher.hash("refresh-token");
        String differentHash = tokenHasher.hash("another-token");

        assertEquals(firstHash, secondHash);
        assertNotEquals(firstHash, differentHash);
    }
}
