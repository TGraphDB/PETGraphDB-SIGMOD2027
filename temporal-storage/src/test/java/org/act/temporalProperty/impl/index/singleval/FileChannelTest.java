package org.act.temporalProperty.impl.index.singleval;

import org.act.temporalProperty.util.BasicSliceOutput;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by song on 2018-01-22.
 */
public class FileChannelTest {

    @Test
    public void main() throws IOException {
        Slice s = new Slice(4);
        s.setInt(0, Integer.MAX_VALUE);
        SliceInput in = s.input();
        Slice t = new Slice(10);
        t.output().writeBytes(in, 4);
        System.out.println(t);
//        FileChannel channel = new FileOutputStream("/tmp/hehe").getChannel();
//
//        ByteBuffer startPos = ByteBuffer.allocate(4);
//        System.out.println(startPos.limit()+" "+startPos.position()+" "+startPos.remaining());
//        startPos.putInt(0, Integer.MAX_VALUE);
//        System.out.println(startPos.limit()+" "+startPos.position()+" "+startPos.remaining());
//        startPos.flip();
//        System.out.println(startPos.limit()+" "+startPos.position()+" "+startPos.remaining());
//        channel.write(startPos);
//
//        startPos.clear();
//        System.out.println(startPos.limit()+" "+startPos.position()+" "+startPos.remaining());
//        startPos.putInt(0, 29677596);
//        System.out.println(startPos.limit()+" "+startPos.position()+" "+startPos.remaining());
//        startPos.flip();
//        System.out.println(startPos.limit()+" "+startPos.position()+" "+startPos.remaining());
//        channel.write(startPos, 0);
//
//        channel.force(true);
//        channel.close();
    }
}
