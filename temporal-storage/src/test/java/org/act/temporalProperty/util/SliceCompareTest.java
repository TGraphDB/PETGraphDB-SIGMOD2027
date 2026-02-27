package org.act.temporalProperty.util;

import org.junit.Test;

public class SliceCompareTest
{
    @Test
    public void compareTest()
    {
        Slice slice1 = new Slice( 8 );
        Slice slice2 = new Slice( 8 );
        slice1.setLong( 0, 254 );
        slice2.setLong( 0, 256 );
        System.out.println(slice1.compareTo( slice2 ));
    }
}
