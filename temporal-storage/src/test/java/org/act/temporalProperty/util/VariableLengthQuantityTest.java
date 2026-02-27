package org.act.temporalProperty.util;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by song on 2018-01-25.
 */
public class VariableLengthQuantityTest {
    private static Logger log = LoggerFactory.getLogger(VariableLengthQuantityTest.class);

    @Test
    public void testIntSize(){
        int last = 0;
        for(int i=0; i<Integer.MAX_VALUE; i++) {
            int size = VariableLengthQuantity.variableLengthSize(i);
            if(size!=last){
                log.info("{} size: {}", i, size);
            }
            last = size;
        }
    }

    @Test
    public void testLongSize(){
        int last = 0;
        for(long i=0; i<Long.MAX_VALUE; i++) {
            int size = VariableLengthQuantity.variableLengthSize(i);
            if(size!=last){
                log.info("{} size: {}", i, size);
            }
            last = size;
        }
    }
}
