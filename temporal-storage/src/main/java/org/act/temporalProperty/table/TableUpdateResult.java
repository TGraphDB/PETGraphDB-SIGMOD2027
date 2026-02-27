package org.act.temporalProperty.table;

import java.nio.ByteBuffer;

import org.act.temporalProperty.util.Slice;

class TableUpdateResult
{
    private final boolean success;
    private final int in_blockOffset;
    private final Slice data;
    
    TableUpdateResult(boolean success, int offset, Slice data)
    {
        this.success = success;
        this.in_blockOffset = offset;
        this.data = data;
    }
    
    ByteBuffer dataAsByteBuffer()
    {
        return this.data.toByteBuffer();
    }
    
    boolean isSuccess()
    {
        return success;
    }
    
    int getInBlockOffset()
    {
        return in_blockOffset;
    }
}
