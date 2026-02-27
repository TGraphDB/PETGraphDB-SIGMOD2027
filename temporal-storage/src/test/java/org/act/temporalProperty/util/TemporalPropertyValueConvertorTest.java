package org.act.temporalProperty.util;

import org.junit.Test;

public class TemporalPropertyValueConvertorTest
{
    @Test
    public void test()
    {
        int i = 0;
        printClasz( i );
        char c = '0';
        printClasz( c );
        double d = 0.0;
        printClasz( d );
        long l = 0;
        printClasz( l );
        byte b = 0;
        printClasz( b );
        float f = 0.0f;
        printClasz( f );
        String s = "0";
        printClasz( s );
        short sh = 0;
        printClasz( sh );
        int[] ii = new int[0];
        printClasz( ii );
        char[] cc = new char[0];
        printClasz( cc );
        double[] dd = new double[0];
        printClasz( dd );
        long[] ll = new long[0];
        printClasz( ll );
        byte[] bb = new byte[0];
        printClasz( bb );
        float[] ff = new float[0];
        printClasz( ff );
        String[] ss = new String[0];
        printClasz( ss );
        short[] shh = new short[0];
        printClasz( shh );
    }
    
    private void printClasz( Object o )
    {
        Class<?> clasz = o.getClass();
        System.out.println( clasz.getSimpleName() );
    }
}
