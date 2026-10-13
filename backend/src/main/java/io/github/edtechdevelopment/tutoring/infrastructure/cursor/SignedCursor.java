package io.github.edtechdevelopment.tutoring.infrastructure.cursor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class SignedCursor {
    private final SecretKeySpec key;
    public SignedCursor(TutoringCursorProperties properties) {
        try (InputStream input = properties.signingKey().getInputStream()) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length < 32) {
                throw new IllegalArgumentException("Cursor signing key must contain at least 32 bytes");
            }
            key = new SecretKeySpec(bytes, "HmacSHA256");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cursor signing key could not be loaded", exception);
        }
    }

    public String encode(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(bytes) + "." + encoder.encodeToString(sign(bytes));
    }

    public String[] decode(String cursor, int fieldCount) {
        if (cursor == null || cursor.length() > 4096) {
            throw new IllegalArgumentException("Invalid cursor");
        }
        String[] parts = cursor.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid cursor");
        }
        byte[] body = Base64.getUrlDecoder().decode(parts[0]);
        byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
        if (!MessageDigest.isEqual(sign(body), signature)) {
            throw new IllegalArgumentException("Invalid cursor signature");
        }
        String[] fields = new String(body, StandardCharsets.UTF_8).split("\\|", -1);
        if (fields.length != fieldCount || !fields[1].equals("1")) {
            throw new IllegalArgumentException("Invalid cursor structure");
        }
        return fields;
    }

    private byte[] sign(byte[] bytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(bytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cursor signing failed", exception);
        }
    }
}
