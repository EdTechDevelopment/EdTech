package io.github.edtechdevelopment.identity.domain.verification.model;

import io.github.edtechdevelopment.identity.domain.verification.exception.InvalidEmailVerificationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationTokenHashTest {

    @Test
    void storesLowercaseSha256HexHash() {
        String hexHash = "0123456789abcdef".repeat(4);

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
                () -> new VerificationTokenHash("a".repeat(32) + " " + "b".repeat(31))
        );
    }

    @Test
    void rejectsHashWithIncorrectLength() {
        String base64UrlHash = "a".repeat(43);
        String hashOverLimit = "a".repeat(65);

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> new VerificationTokenHash(base64UrlHash)
        );
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> new VerificationTokenHash(hashOverLimit)
        );
    }

    @Test
    void rejectsNonHexAndUppercaseCharacters() {
        String nonHexHash = "g".repeat(64);
        String uppercaseHash = "A".repeat(64);

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> new VerificationTokenHash(nonHexHash)
        );
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> new VerificationTokenHash(uppercaseHash)
        );
    }

    @Test
    void doesNotExposeHashInToString() {
        String hash = "0123456789abcdef".repeat(4);

        String printedHash = new VerificationTokenHash(hash).toString();

        assertEquals("VerificationTokenHash[PROTECTED]", printedHash);
        assertTrue(!printedHash.contains(hash));
    }
}
