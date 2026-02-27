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
package org.act.temporalProperty.util;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Compress, decompress using google's xerial.Snappy lib
 */
public final class Snappy
{
    public static void uncompress(ByteBuffer compressed, ByteBuffer uncompressed) throws IOException
    {
        org.xerial.snappy.Snappy.uncompress(compressed, uncompressed);
    }

    public static void uncompress(byte[] input, int inputOffset, int length, byte[] output, int outputOffset) throws IOException
    {
        org.xerial.snappy.Snappy.uncompress(input, inputOffset, length, output, outputOffset);
    }

    public static int compress(byte[] input, int inputOffset, int length, byte[] output, int outputOffset) throws IOException
    {
        return org.xerial.snappy.Snappy.compress(input, inputOffset, length, output, outputOffset);
    }

    public static byte[] compress(String text) throws IOException
    {
        return org.xerial.snappy.Snappy.compress(text);
    }

    public static int maxCompressedLength(int length)
    {
        return org.xerial.snappy.Snappy.maxCompressedLength(length);
    }
}
