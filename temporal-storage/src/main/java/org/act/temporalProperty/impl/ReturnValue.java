package org.act.temporalProperty.impl;

import org.act.temporalProperty.util.Slice;

public class ReturnValue
{
    private Slice value;
    boolean isNull;
    public ReturnValue( Slice v )
    {
        this.value = v;
        isNull = false;
    }
    public ReturnValue( boolean b )
    {
        this.isNull = true;
    }
    public Slice getValue()
    {
        if(!isNull)
            return value;
        else
            return null;
    }
    public boolean isNull()
    {
        return isNull;
    }
    
}
