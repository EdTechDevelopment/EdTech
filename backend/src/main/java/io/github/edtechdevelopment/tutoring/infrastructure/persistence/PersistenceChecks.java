package io.github.edtechdevelopment.tutoring.infrastructure.persistence;

public final class PersistenceChecks {
    private PersistenceChecks() {
    }

    public static void requireOneRow(int count) {
        if (count != 1) {
            throw new InvalidPersistenceDataException("Expected exactly one affected row");
        }
    }

    public static boolean hasConstraint(Throwable failure, String name) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(name)) {
                return true;
            }
        }
        return false;
    }
}
