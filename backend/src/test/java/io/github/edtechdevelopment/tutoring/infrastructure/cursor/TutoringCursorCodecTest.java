package io.github.edtechdevelopment.tutoring.infrastructure.cursor;

import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvalidInvitationCursorException;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.*;
import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileCursorException;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.*;
import io.github.edtechdevelopment.tutoring.application.relationship.exception.InvalidRelationshipCursorException;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.*;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TutoringCursorCodecTest {

    private final SignedCursor signing = new SignedCursor(new TutoringCursorProperties(new ByteArrayResource(new byte[32])));
    private final Instant now = Instant.parse("2026-10-13T10:00:00Z");

    @Test
    void bindsPublicCursorToTypeFiltersAndSigningKey() {
        HmacPublicProfileCursorCodec codec = new HmacPublicProfileCursorCodec(signing);
        PublicProfileSearchQuery query = new PublicProfileSearchQuery("MATH", 18, 30, null);
        PublicProfilePageCursor position = new PublicProfilePageCursor(now, UUID.randomUUID(), LocalDate.of(2026, 10, 13));
        String cursor = codec.encode(position, ProfileType.TEACHER, query);
        assertThat(codec.decode(cursor, ProfileType.TEACHER, query)).isEqualTo(position);
        assertThatThrownBy(() -> codec.decode(cursor, ProfileType.STUDENT, query)).isInstanceOf(InvalidPublicProfileCursorException.class);
        assertThatThrownBy(() -> codec.decode(cursor, ProfileType.TEACHER, new PublicProfileSearchQuery("MATH", 19, 30, null)))
                .isInstanceOf(InvalidPublicProfileCursorException.class);
        byte[] anotherKey = new byte[32];
        Arrays.fill(anotherKey, (byte) 1);
        HmacPublicProfileCursorCodec anotherCodec = new HmacPublicProfileCursorCodec(new SignedCursor(
                new TutoringCursorProperties(new ByteArrayResource(anotherKey))));
        assertThatThrownBy(() -> anotherCodec.decode(cursor, ProfileType.TEACHER, query)).isInstanceOf(InvalidPublicProfileCursorException.class);
        String[] parts = cursor.split("\\.");
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString("changed".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[1];
        assertThatThrownBy(() -> codec.decode(tampered, ProfileType.TEACHER, query)).isInstanceOf(InvalidPublicProfileCursorException.class);
    }

    @Test
    void bindsInvitationCursorToOwnerDirectionAndStatus() {
        HmacInvitationCursorCodec codec = new HmacInvitationCursorCodec(signing);
        UUID owner = UUID.randomUUID();
        InvitationPageCursor position = new InvitationPageCursor(now, UUID.randomUUID(), now);
        String cursor = codec.encode(position, owner, InvitationDirection.INCOMING, InvitationStatus.PENDING);
        assertThat(codec.decode(cursor, owner, InvitationDirection.INCOMING, InvitationStatus.PENDING)).isEqualTo(position);
        assertThatThrownBy(() -> codec.decode(cursor, UUID.randomUUID(), InvitationDirection.INCOMING, InvitationStatus.PENDING))
                .isInstanceOf(InvalidInvitationCursorException.class);
        assertThatThrownBy(() -> codec.decode(cursor, owner, InvitationDirection.SENT, InvitationStatus.PENDING))
                .isInstanceOf(InvalidInvitationCursorException.class);
        assertThatThrownBy(() -> codec.decode(cursor, owner, InvitationDirection.INCOMING, InvitationStatus.REJECTED))
                .isInstanceOf(InvalidInvitationCursorException.class);
    }

    @Test
    void bindsRelationshipCursorToOwnerAndDirection() {
        HmacRelationshipCursorCodec codec = new HmacRelationshipCursorCodec(signing);
        UUID owner = UUID.randomUUID();
        RelationshipPageCursor position = new RelationshipPageCursor(now, UUID.randomUUID());
        String cursor = codec.encode(position, owner, RelationshipDirection.TEACHER_STUDENTS);
        assertThat(codec.decode(cursor, owner, RelationshipDirection.TEACHER_STUDENTS)).isEqualTo(position);
        assertThatThrownBy(() -> codec.decode(cursor, UUID.randomUUID(), RelationshipDirection.TEACHER_STUDENTS))
                .isInstanceOf(InvalidRelationshipCursorException.class);
        assertThatThrownBy(() -> codec.decode(cursor, owner, RelationshipDirection.STUDENT_TEACHERS))
                .isInstanceOf(InvalidRelationshipCursorException.class);
    }
}

