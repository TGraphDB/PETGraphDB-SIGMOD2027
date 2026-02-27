/*
 * Copyright (C) 2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.act.temporalProperty.impl;

import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.util.Slice;

/**
 * Types that a time point entry value can be.
 */
public enum ValueType
{
    /**
     * UNKNOWN occurs when system convert a time interval modification operation into time point entries, because
     * temporal properties are stored as list of time point entries on disk files.
     *
     * For example, if we have temporal property <9, A>, <20, B> (9 and 20 is start time point, A and B is value) and
     * perform a time interval modification:
     * setProperty(12, 16, C) (12 is start, 16 is end, C is value)
     * then we got:
     * <9, A>,  <12, C>, <17(UNKNOWN), ?>, <20, B>
     * so 17 is marked UNKNOWN, and its value is either:
     * - queried by system before apply the time interval operation. or
     * - left with any random value which should not be relied on, should query low-level disk layer for correct value.
     *
     * Note
     * - 12 is **NOT** marked UNKNOWN.
     * - UNKNOWN mark is convert into actual value type or INVALID when the entry is written to disk.
     */
    UNKNOWN( 0 ),
    INVALID( 1 ),
    // VALUE means we do not know which exact type the value is, it may be followings.
    VALUE( 2 ),
    INT( 3 ),
    LONG( 4 ),
    FLOAT( 5 ),
    DOUBLE( 6 ),
    SLICE( 7 );

    public static ValueType getValueTypeByPersistentId( int persistentId )
    {
        persistentId = persistentId & 7;
        switch ( persistentId )
        {
        case 0:
            return UNKNOWN;
        case 1:
            return INVALID;
        case 2:
            return VALUE;
        case 3:
            return INT;
        case 4:
            return LONG;
        case 5:
            return FLOAT;
        case 6:
            return DOUBLE;
        case 7:
            return SLICE;
        default:
            throw new IllegalArgumentException( "invalid persistentId!" );
        }
    }

    private final int persistentId;

    ValueType( int persistentId )
    {
        this.persistentId = persistentId;
    }

    public int getPersistentId()
    {
        return persistentId;
    }

    public boolean isValue()
    {
        return persistentId >= 2;
    }

    public boolean isExactValueType()
    {
        return persistentId >= 3;
    }

    public ValueContentType toValueContentType()
    {
        if ( persistentId >= 3 )
        {
            return ValueContentType.decode( persistentId - 2 );
        }
        else
        {
            throw new TPSNHException( "can not convert to ValueContentType! got {}", this );
        }
    }

    public static ValueType fromValueContentType( ValueContentType valueContentType )
    {
        return getValueTypeByPersistentId( valueContentType.getId() + 2 );
    }

    public static ValueType fromValue(Object obj){
        if(obj instanceof Integer) return INT;
        if(obj instanceof Float) return FLOAT;
        if(obj instanceof Double) return DOUBLE;
        if(obj instanceof Long) return LONG;
        if(obj instanceof Slice) return SLICE;
        else return UNKNOWN;
    }
}
