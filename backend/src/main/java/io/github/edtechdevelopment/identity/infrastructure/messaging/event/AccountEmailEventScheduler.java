package io.github.edtechdevelopment.identity.infrastructure.messaging.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "identity.events.scheduler-enabled", havingValue = "true")
public class AccountEmailEventScheduler {
    private final AccountEmailEventDispatcher dispatcher;
    public AccountEmailEventScheduler(AccountEmailEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${identity.events.poll-delay:10s}", initialDelayString = "${identity.events.initial-delay:10s}")
    public void processPending() {
        dispatcher.processPending();
    }
}
