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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.act.temporalProperty.util.PureJavaCrc32C;
import org.act.temporalProperty.util.Slice;

public final class Logs
{
    private Logs()
    {
    }

    
    public static synchronized LogWriter createLogWriter( String dbDir, boolean isStableLevel )
            throws IOException
    {
        String fileName;
        if( isStableLevel )
        {
//            fileName = dbDir + "/" + Filename.logFileName( 1 );
            fileName = dbDir + "/" + "stable.new.meta";
        }
        else
        {
//            fileName = dbDir + "/" + Filename.logFileName( 0 );
            fileName = dbDir + "/" + "unstable.new.meta";
        }
        File file = new File(fileName);
        if (false) 
        {
            return new MMapLogWriter(file, 0);
        }
        else {
            return new FileChannelLogWriter(file, 0);
        }
    }
    public static LogWriter createMetaWriter(File file) throws FileNotFoundException {
        return new FileChannelLogWriter(file, 0);
    }

    public static int getChunkChecksum(int chunkTypeId, Slice slice)
    {
        return getChunkChecksum(chunkTypeId, slice.getRawArray(), slice.getRawOffset(), slice.length());
    }

    public static int getChunkChecksum(int chunkTypeId, byte[] buffer, int offset, int length)
    {
        // Compute the crc of the record type and the payload.
        PureJavaCrc32C crc32C = new PureJavaCrc32C();
        crc32C.update(chunkTypeId);
        crc32C.update(buffer, offset, length);
        return crc32C.getMaskedValue();
    }
}
