package org.act.temporalProperty.util;

public enum ConstantTime
{
    NOW(-1);
    private int value;
    private ConstantTime( int v )
    {
        this.value = v;
    }
    public int getValue()
    {
        return this.value;
    }
}
