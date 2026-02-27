package org.act.temporalProperty.util;

import java.util.Random;

import junit.framework.Assert;

import org.act.temporalProperty.impl.SequenceNumber;
import org.act.temporalProperty.impl.ValueType;
import org.junit.Before;
import org.junit.Test;

public class SequenceNumberTest
{
    
    private final int NUM = 10000;
    
    private int[] times = new int[NUM];
    private ValueType[] valueTypes = new ValueType[NUM];
    private long[] sequences = new long[NUM];
    
    @Before
    public void setUp()
    {
        Random random = new Random( NUM );
        for( int i = 0; i<NUM; i++ )
        {
            times[i] = random.nextInt( Integer.MAX_VALUE );
            if( times[i] % 2 == 0 )
            {
                valueTypes[i] = ValueType.VALUE;
            }else
            {
                valueTypes[i] = ValueType.INVALID;
            }
            sequences[i] = SequenceNumber.packTimeAndValueType( times[i], valueTypes[i] );
        }
    }
    
    @Test
    public void test()
    {
        for( int i = 0; i<NUM; i++ )
        {
            int time = (int) SequenceNumber.unpackTime( sequences[i] );
            ValueType type = SequenceNumber.unpackValueType( sequences[i] );
            Assert.assertEquals( times[i], time );
            Assert.assertEquals( valueTypes[i], type );
        }
    }
}
