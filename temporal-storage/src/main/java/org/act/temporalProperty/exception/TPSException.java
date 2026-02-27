package org.act.temporalProperty.exception;

import org.slf4j.helpers.MessageFormatter;

/**
 * Created by song on 2018-01-17.
 */
public class TPSException extends Exception {

    public TPSException(String s) {
        super(s);
    }

    public TPSException(String msg, Throwable e){
        super(msg, e);
    }

    public TPSException(String msg, Object... o){
        super(MessageFormatter.arrayFormat(msg, o).getMessage());
    }

}
