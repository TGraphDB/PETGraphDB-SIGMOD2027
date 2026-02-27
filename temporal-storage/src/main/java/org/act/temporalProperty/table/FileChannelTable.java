
package org.act.temporalProperty.table;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Comparator;

import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.Slices;
import org.act.temporalProperty.util.Snappy;

import static org.act.temporalProperty.impl.CompressionType.SNAPPY;

/**
 * 以FileChannel形式打开文件并读取信息的工具类，以Block为单位对文件进行读取。
 *
 */
public class FileChannelTable
        extends Table
{
    public FileChannelTable(String name, FileChannel fileChannel, Comparator<Slice> comparator, boolean verifyChecksums)
            throws IOException
    {
        super(name, fileChannel, comparator, verifyChecksums);
    }

    @Override
    protected Footer init()
            throws IOException
    {
        long size = fileChannel.size();
        ByteBuffer footerData = read(size - Footer.ENCODED_LENGTH, Footer.ENCODED_LENGTH);
        return Footer.readFooter(Slices.copiedBuffer(footerData));
    }

    @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "NonPrivateFieldAccessedInSynchronizedContext"})
    @Override
    protected Block readBlock(BlockHandle blockHandle)
            throws IOException
    {
        // read block trailer
        ByteBuffer trailerData = read(blockHandle.getOffset() + blockHandle.getDataSize(), BlockTrailer.ENCODED_LENGTH);
        BlockTrailer blockTrailer = BlockTrailer.readBlockTrailer(Slices.copiedBuffer(trailerData));

// todo re-enable crc check when ported to support direct buffers
//        // only verify check sums if explicitly asked by the user
//        if (verifyChecksums) {
//            // checksum data and the compression type in the trailer
//            PureJavaCrc32C checksum = new PureJavaCrc32C();
//            checksum.update(data.getRawArray(), data.getRawOffset(), blockHandle.getDataSize() + 1);
//            int actualCrc32c = checksum.getMaskedValue();
//
//            Preconditions.checkState(blockTrailer.getCrc32c() == actualCrc32c, "Block corrupted: checksum mismatch");
//        }

        // decompress data

        ByteBuffer uncompressedBuffer = read(blockHandle.getOffset(), blockHandle.getDataSize());
        Slice uncompressedData;
        if (blockTrailer.getCompressionType() == SNAPPY) {
            int uncompressedLength = uncompressedLength(uncompressedBuffer);
            ByteBuffer uncompressedScratch = ByteBuffer.allocateDirect(uncompressedLength);
            uncompressedScratch.clear();

            Snappy.uncompress(uncompressedBuffer, uncompressedScratch);
            uncompressedData = Slices.copiedBuffer(uncompressedScratch);
        }
        else {
            uncompressedData = Slices.copiedBuffer(uncompressedBuffer);
        }

        return new Block(uncompressedData, comparator);
    }

    private ByteBuffer read(long offset, int length)
            throws IOException
    {
        ByteBuffer uncompressedBuffer = ByteBuffer.allocate(length);
        fileChannel.read(uncompressedBuffer, offset);
        if (uncompressedBuffer.hasRemaining()) {
            throw new IOException("Could not read all the data");
        }
        uncompressedBuffer.clear();
        return uncompressedBuffer;
    }

    @Override
    protected IndexBlock readIndexBlock( BlockHandle blockHandle ) throws IOException
    {
     // read block trailer
        ByteBuffer trailerData = read(blockHandle.getOffset() + blockHandle.getDataSize(), BlockTrailer.ENCODED_LENGTH);
        BlockTrailer blockTrailer = BlockTrailer.readBlockTrailer(Slices.copiedBuffer(trailerData));

// todo re-enable crc check when ported to support direct buffers
//        // only verify check sums if explicitly asked by the user
//        if (verifyChecksums) {
//            // checksum data and the compression type in the trailer
//            PureJavaCrc32C checksum = new PureJavaCrc32C();
//            checksum.update(data.getRawArray(), data.getRawOffset(), blockHandle.getDataSize() + 1);
//            int actualCrc32c = checksum.getMaskedValue();
//
//            Preconditions.checkState(blockTrailer.getCrc32c() == actualCrc32c, "Block corrupted: checksum mismatch");
//        }

        // decompress data

        ByteBuffer uncompressedBuffer = read(blockHandle.getOffset(), blockHandle.getDataSize());
        Slice uncompressedData;
        if (blockTrailer.getCompressionType() == SNAPPY) {
            int uncompressedLength = uncompressedLength(uncompressedBuffer);
            ByteBuffer uncompressedScratch = ByteBuffer.allocateDirect(uncompressedLength);
            uncompressedScratch.clear();

            Snappy.uncompress(uncompressedBuffer, uncompressedScratch);
            uncompressedData = Slices.copiedBuffer(uncompressedScratch);
        }
        else {
            uncompressedData = Slices.copiedBuffer(uncompressedBuffer);
        }

        return new IndexBlock(uncompressedData, comparator);
    }

    @Override
    public void close()
    {
        try
        {
            fileChannel.close();
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
    }
}
