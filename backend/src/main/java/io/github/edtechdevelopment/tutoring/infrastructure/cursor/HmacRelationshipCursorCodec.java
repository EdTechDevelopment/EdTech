package io.github.edtechdevelopment.tutoring.infrastructure.cursor;

import io.github.edtechdevelopment.tutoring.application.relationship.exception.InvalidRelationshipCursorException;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipDirection;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipPageCursor;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.RelationshipCursorCodec;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class HmacRelationshipCursorCodec implements RelationshipCursorCodec {
    private final SignedCursor signing;
    public HmacRelationshipCursorCodec(SignedCursor signing) {
        this.signing = signing;
    }

    @Override
    public String encode(RelationshipPageCursor cursor, UUID owner, RelationshipDirection direction) {
        return signing.encode("relationships|1|" + owner + "|" + direction.name() + "|" + cursor.createdAt() + "|" + cursor.otherUserId());
    }
    @Override
    public RelationshipPageCursor decode(String cursor, UUID owner, RelationshipDirection direction) {
        try {
            String[] fields = signing.decode(cursor, 6);
            if (!fields[0].equals("relationships") || !fields[2].equals(owner.toString()) || !fields[3].equals(direction.name())) {
                throw new InvalidRelationshipCursorException();
            }
            return new RelationshipPageCursor(Instant.parse(fields[4]), UUID.fromString(fields[5]));
        } catch (RuntimeException exception) {
            throw new InvalidRelationshipCursorException();
        }
    }
}
