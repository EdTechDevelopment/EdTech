package io.github.edtechdevelopment.tutoring.application.profile.port.out;

import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfilePageCursor;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchQuery;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;

public interface PublicProfileCursorCodec {

    PublicProfilePageCursor decode(String encodedCursor, ProfileType profileType, PublicProfileSearchQuery query);

    String encode(PublicProfilePageCursor cursor, ProfileType profileType, PublicProfileSearchQuery query);
}
