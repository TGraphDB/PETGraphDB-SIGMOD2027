package org.act.temporalProperty.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

import org.act.temporalProperty.util.FileUtils;
import org.act.temporalProperty.util.Slice;
import org.junit.BeforeClass;
import org.junit.Test;

public class LogWriterAndReaderTest
{
    private final static String dbDir = "./target/LogWriterAndReaderTest/";
    private static List<Long> added = new LinkedList<Long>();
    private static List<Long> deleted = new LinkedList<Long>();
    
    @BeforeClass
    public static void setUp()
    {
        File db = new File(dbDir);
        if( db.exists() )
            FileUtils.deleteDirectoryContents( db );
        db.mkdirs();
        for( long i = 0; i<10; i++ )
            added.add( i );
        for( long i = 10; i<20; i++ )
            deleted.add( i );
    }
    
    @Test
    public void test() throws IOException
    {
        LogWriter writer = Logs.createLogWriter( dbDir, true );
        for( long i : added )
        {
//            VersionEdit edit = new VersionEdit();
//            FileMetaData fileMetaData = new FileMetaData( i, (int)i, (int)i, (int)i );
//            edit.addFile( 0, fileMetaData );
//            writer.addRecord( edit.encode(), true );
        }
        for( long i : deleted )
        {
//            VersionEdit edit = new VersionEdit();
//            edit.deleteFile( 0, i );
//            writer.addRecord( edit.encode(), true );
        }
        writer.close();
        File logFile = new File(dbDir + "/unstable.meta");
        FileChannel channel = new FileInputStream( logFile ).getChannel();
        LogReader reader = new LogReader( channel, null, false, 0 );
        Slice s;
        while( (s = reader.readRecord()) != null )
        {
//            VersionEdit edit = new VersionEdit( s );
//            System.out.println( edit.getNewFiles() );
//            System.out.println( edit.getDeletedFiles() );
        }
    }
}
