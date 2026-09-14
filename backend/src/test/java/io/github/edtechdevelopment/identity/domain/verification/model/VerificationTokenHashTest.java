package io.github.edtechdevelopment.identity.domain.verification.model;

import io.github.edtechdevelopment.identity.domain.verification.exception.InvalidEmailVerificationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationTokenHashTest {

    @Test
    void storesHashWithoutChoosingItsEncoding() {
        String base64UrlHash = "a".repeat(43);
        String hexHash = "b".repeat(64);

        assertEquals(base64UrlHash, new VerificationTokenHash(base64UrlHash).value());
        assertEquals(hexHash, new VerificationTokenHash(hexHash).value());
    }

    @Test
    void rejectsNullBlankAndWhitespace() {
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> new VerificationTokenHash(null)
        );
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> new VerificationTokenHash("   ")
        );
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> new VerificationTokenHash("hash with whitespace")
        );
    }

    @Test
    void rejectsHashLongerThanDatabaseColumn() {
        String hashOverLimit = "a".repeat(65);

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> new VerificationTokenHash(hashOverLimit)
        );
    }

    @Test
    void doesNotExposeHashInToString() {
        String hash = "sensitive-token-hash";

        String printedHash = new VerificationTokenHash(hash).toString();

        assertEquals("VerificationTokenHash[PROTECTED]", printedHash);
        assertTrue(!printedHash.contains(hash));
    }
}
