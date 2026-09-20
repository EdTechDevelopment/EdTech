package io.github.edtechdevelopment.identity.domain.user.model;

import io.github.edtechdevelopment.identity.domain.user.exception.InvalidEmailException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @Test
    void normalizesEmail() {
        Email email = new Email("  Anna@Example.COM  ");

        assertEquals("anna@example.com", email.value());
    }

    @Test
    void comparesEmailsByNormalizedValue() {
        Email first = new Email("Anna@Example.com");
        Email second = new Email("anna@example.com");

        assertEquals(first, second);
    }

    @Test
    void rejectsNullEmail() {
        assertThrows(InvalidEmailException.class, () -> new Email(null));
    }

    @Test
    void rejectsInvalidEmailFormat() {
        List<String> invalidEmails = List.of(
            "",
            "   ",
            "anna.example.com",
            "anna@@example.com",
            "@example.com",
            "anna@",
            "anna @example.com",
            "anna@exam ple.com",
            "a".repeat(250) + "@b.com"
        );

        for (String invalidEmail : invalidEmails) {
            assertThrows(InvalidEmailException.class, () -> new Email(invalidEmail), () -> "Expected invalid email to be rejected");
        }
    }
}
