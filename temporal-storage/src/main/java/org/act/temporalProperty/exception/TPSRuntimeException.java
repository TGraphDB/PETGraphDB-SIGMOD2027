package org.act.temporalProperty.exception;

import org.slf4j.helpers.MessageFormatter;


/**
 * Created by song on 2018-01-17.
 */
public class TPSRuntimeException extends RuntimeException {
    public TPSRuntimeException(String msg, Throwable e){
        super(msg, e);
    }
    public TPSRuntimeException(String msg){
        super(msg);
    }
    public TPSRuntimeException(String msg, Object... o){
        super(MessageFormatter.arrayFormat(msg, o).getMessage());
    }

}
