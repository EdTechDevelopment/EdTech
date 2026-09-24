package io.github.edtechdevelopment.notifications.application.port.out.email;

public interface VerificationEmailSender {

    void send(VerificationEmailMessage message);
}
