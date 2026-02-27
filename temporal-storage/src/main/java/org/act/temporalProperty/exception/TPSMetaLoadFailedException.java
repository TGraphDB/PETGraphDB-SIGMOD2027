package org.act.temporalProperty.exception;

/**
 * Created by song on 2018-01-17.
 */
public class TPSMetaLoadFailedException extends TPSException {

    public TPSMetaLoadFailedException(String s) {
        super(s);
    }

    public TPSMetaLoadFailedException(String msg, Throwable e) {
        super(msg, e);
    }
}
