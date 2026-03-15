package com.boombapcompile.blckvox.exception;

/**
 * Thrown when an invalid state transition is attempted.
 *
 * @since 1.2
 */
public class InvalidStateTransitionException extends BlckvoxException {

    public InvalidStateTransitionException(String from, String to) {
        super("Invalid state transition: " + from + " → " + to);
    }
}
