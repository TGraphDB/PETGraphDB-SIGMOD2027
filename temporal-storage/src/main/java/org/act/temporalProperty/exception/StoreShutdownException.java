package org.act.temporalProperty.exception;

public class StoreShutdownException extends TPSRuntimeException {
    public StoreShutdownException() {
        super("store already shutdown!");
    }
}
