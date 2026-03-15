package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.exception.InvalidStateTransitionException;
import com.boombapcompile.blckvox.service.orchestration.event.ApplicationStateChangedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe tracker for the application's high-level state.
 * Publishes {@link ApplicationStateChangedEvent} on every transition.
 * Validates that state transitions follow the allowed state machine.
 *
 * @since 1.2
 */
@Service
public class ApplicationStateTracker {

    private static final Logger LOG = LogManager.getLogger(ApplicationStateTracker.class);

    /** Valid state transitions: from -> allowed targets. */
    private static final Map<ApplicationState, Set<ApplicationState>> VALID_TRANSITIONS = Map.of(
            ApplicationState.IDLE, Set.of(ApplicationState.RECORDING),
            ApplicationState.RECORDING, Set.of(ApplicationState.IDLE, ApplicationState.TRANSCRIBING),
            ApplicationState.TRANSCRIBING, Set.of(ApplicationState.IDLE)
    );

    private final ApplicationEventPublisher publisher;
    private volatile ApplicationState state = ApplicationState.IDLE;

    public ApplicationStateTracker(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Transitions to a new state and publishes an event.
     * If the new state equals the current state, this is a no-op.
     *
     * @param newState the state to transition to
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public synchronized void transitionTo(ApplicationState newState) {
        ApplicationState previous = this.state;
        if (previous == newState) {
            return;
        }
        Set<ApplicationState> allowed = VALID_TRANSITIONS.get(previous);
        if (!allowed.contains(newState)) {
            throw new InvalidStateTransitionException(previous.name(), newState.name());
        }
        this.state = newState;
        LOG.info("State transition: {} → {}", previous, newState);
        publisher.publishEvent(new ApplicationStateChangedEvent(previous, newState, Instant.now()));
    }

    public ApplicationState getState() {
        return state;
    }
}
