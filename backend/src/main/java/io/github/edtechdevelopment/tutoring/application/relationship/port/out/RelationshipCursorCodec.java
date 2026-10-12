package io.github.edtechdevelopment.tutoring.application.relationship.port.out;

import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipDirection;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipPageCursor;

import java.util.UUID;

public interface RelationshipCursorCodec {

    RelationshipPageCursor decode(String encodedCursor, UUID ownerUserId, RelationshipDirection direction);

    String encode(RelationshipPageCursor cursor, UUID ownerUserId, RelationshipDirection direction);
}
