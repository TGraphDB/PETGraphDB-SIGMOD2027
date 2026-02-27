package org.act.temporalProperty.exception;

import org.slf4j.helpers.MessageFormatter;

/**
 * Created by song on 2018-01-17.
 */
public class TPSNHException extends TPSRuntimeException {
    public TPSNHException(String msg, Object... o){
        super(MessageFormatter.arrayFormat(msg, o).getMessage());
    }

    public TPSNHException(String msg) {
        super(msg);
    }

    public TPSNHException(String msg, Throwable e) {
        super(msg, e);
    }
}
