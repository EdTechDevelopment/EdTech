package io.github.edtechdevelopment.identity.infrastructure.messaging.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Component
public class AccountEmailEventDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(AccountEmailEventDispatcher.class);
    private final AccountEmailEventOutbox outbox;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public AccountEmailEventDispatcher(AccountEmailEventOutbox outbox, ApplicationEventPublisher publisher,
                                      PlatformTransactionManager transactionManager, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void processPending() {
        for (UUID id : outbox.findReadyIds(clock.instant(), 10)) {
            try {
                transactions.executeWithoutResult(status -> {
                    var event = outbox.findReadyForUpdate(id, clock.instant());
                    if (event.isEmpty()) {
                        return;
                    }
                    publisher.publishEvent(event.get());
                    outbox.markPublished(id, clock.instant());
                });
            } catch (RuntimeException exception) {
                outbox.retryLater(id, clock.instant().plus(Duration.ofSeconds(10)));
                LOG.warn("Account email event delivery will be retried, eventId={}", id);
            }
        }
    }
}
