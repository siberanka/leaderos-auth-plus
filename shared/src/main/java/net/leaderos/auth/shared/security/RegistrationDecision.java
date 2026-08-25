package net.leaderos.auth.shared.security;

import java.util.Collections;
import java.util.List;

public final class RegistrationDecision {

    public enum Status {
        ALLOWED,
        LIMIT_REACHED,
        ALREADY_PENDING,
        SECURITY_ERROR
    }

    private final Status status;
    private final String reservationToken;
    private final int accountCount;
    private final List<String> accountNames;

    private RegistrationDecision(Status status, String reservationToken, int accountCount,
            List<String> accountNames) {
        this.status = status;
        this.reservationToken = reservationToken;
        this.accountCount = accountCount;
        this.accountNames = accountNames == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(accountNames);
    }

    public static RegistrationDecision allowed(String token, int count, List<String> names) {
        return new RegistrationDecision(Status.ALLOWED, token, count, names);
    }

    public static RegistrationDecision denied(Status status, int count, List<String> names) {
        return new RegistrationDecision(status, null, count, names);
    }

    public boolean isAllowed() {
        return status == Status.ALLOWED;
    }

    public Status getStatus() {
        return status;
    }

    public String getReservationToken() {
        return reservationToken;
    }

    public int getAccountCount() {
        return accountCount;
    }

    public List<String> getAccountNames() {
        return accountNames;
    }
}
