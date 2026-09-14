package io.github.edtechdevelopment.identity.domain.user.model;

import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordHashTest {

    @Test
    void storesPasswordHash() {
        String hashValue = "$2b$12$example-password-hash";

        PasswordHash passwordHash = new PasswordHash(hashValue);

        assertEquals(hashValue, passwordHash.value());
    }

    @Test
    void rejectsNullPasswordHash() {
        assertThrows(InvalidUserDataException.class, () -> new PasswordHash(null));
    }

    @Test
    void rejectsBlankPasswordHash() {
        List<String> blankValues = List.of("", " ", "\t", "\n");

        for (String blankValue : blankValues) {
            assertThrows(InvalidUserDataException.class, () -> new PasswordHash(blankValue), () -> "Expected blank password hash to be rejected");
        }
    }

    @Test
    void doesNotExposeHashInStringRepresentation() {
        String hashValue = "$2b$12$sensitive-password-hash";
        PasswordHash passwordHash = new PasswordHash(hashValue);

        assertFalse(passwordHash.toString().contains(hashValue));
        assertEquals("PasswordHash[PROTECTED]", passwordHash.toString());
    }
}
