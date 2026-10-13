package io.github.edtechdevelopment.tutoring.infrastructure.cursor;

import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileCursorException;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfilePageCursor;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchQuery;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.PublicProfileCursorCodec;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class HmacPublicProfileCursorCodec implements PublicProfileCursorCodec {
    private final SignedCursor signing;
    public HmacPublicProfileCursorCodec(SignedCursor signing) {
        this.signing = signing;
    }

    private String context(ProfileType type, PublicProfileSearchQuery query) {
        return type.name() + "|" + (query.subjectCode() == null ? "" : query.subjectCode()) + "|"
                + (query.minAge() == null ? "" : query.minAge()) + "|" + (query.maxAge() == null ? "" : query.maxAge());
    }
    @Override
    public String encode(PublicProfilePageCursor cursor, ProfileType type, PublicProfileSearchQuery query) {
        return signing.encode("profiles|1|" + context(type, query) + "|" + cursor.createdAt() + "|" + cursor.userId() + "|" + cursor.ageAsOf());
    }
    @Override
    public PublicProfilePageCursor decode(String cursor, ProfileType type, PublicProfileSearchQuery query) {
        try {
            String[] fields = signing.decode(cursor, 9);
            if (!fields[0].equals("profiles") || !(fields[2] + "|" + fields[3] + "|" + fields[4] + "|" + fields[5]).equals(context(type, query))) {
                throw new InvalidPublicProfileCursorException();
            }
            return new PublicProfilePageCursor(Instant.parse(fields[6]), UUID.fromString(fields[7]), LocalDate.parse(fields[8]));
        } catch (RuntimeException exception) {
            throw new InvalidPublicProfileCursorException();
        }
    }
}
