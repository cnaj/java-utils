package utils.io;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import utils.io.testutils.BufferStreamReaderStrategy;
import utils.io.testutils.StreamReaderStrategy;

public class LazyFileBufferedInputStreamTest
{

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ByteArrayInputStream byteStream;
    private byte[] origBytes;
    private StreamReaderStrategy reader;

    @Before
    public void before()
    {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = (byte) (i - 128);
        }
        this.origBytes = data;
        this.byteStream = new ByteArrayInputStream(this.origBytes);

        this.reader = new BufferStreamReaderStrategy();
    }

    @Test
    public void testMarkSupported() throws IOException
    {
        InputStream is = mock(InputStream.class);
        LazyFileBufferedInputStream lfbis = new LazyFileBufferedInputStream(is, null);
        try
        {
            assertTrue(lfbis.markSupported());
        }
        finally
        {
            lfbis.close();
        }
    }

    @Test
    public void testWrappedStreamIsClosedOnClose() throws IOException
    {
        InputStream is = mock(InputStream.class);
        LazyFileBufferedInputStream lfbis = new LazyFileBufferedInputStream(is, null);

        lfbis.close();

        verify(is).close();
    }

    @Test
    public void testFileNotCreatedWhileBufferSuffices() throws IOException
    {
        final int bufferSize = 10;

        LazyFileBufferedInputStream lfbis = new LazyFileBufferedInputStream(this.byteStream, bufferSize, null);
        try
        {
            lfbis.mark(Integer.MAX_VALUE);
            this.reader.take(bufferSize, lfbis);

            assertNull(lfbis.getCacheFile());
        }
        finally
        {
            lfbis.close();
        }
    }

    @Test
    public void testResetWithoutMarkFails() throws IOException
    {
        InputStream is = new ByteArrayInputStream(new byte[] { 0, 1, 2, 3 });
        LazyFileBufferedInputStream lfbis = new LazyFileBufferedInputStream(is, null);
        try
        {
            lfbis.read();

            this.thrown.expect(IOException.class);
            lfbis.reset();
        }
        finally
        {
            lfbis.close();
        }
    }

    @Test
    public void testResetAfterCloseFails() throws IOException
    {
        LazyFileBufferedInputStream lfbis = new LazyFileBufferedInputStream(mock(InputStream.class), null);
        try
        {
            lfbis.mark(Integer.MAX_VALUE);
        }
        finally
        {
            lfbis.close();
        }

        this.thrown.expect(IOException.class);
        lfbis.reset();
    }

    @Test
    public void testCacheFileIsRemovedOnClose() throws IOException
    {
        final int bufferSize = 10;
        assertTrue(bufferSize < this.origBytes.length);

        File cacheFile;
        LazyFileBufferedInputStream lfbis = new LazyFileBufferedInputStream(this.byteStream, bufferSize, null);
        try
        {
            lfbis.mark(Integer.MAX_VALUE);
            this.reader.drain(lfbis);
            lfbis.flush();

            cacheFile = lfbis.getCacheFile();

            assertNotNull(cacheFile);
            assertTrue(cacheFile.exists());
        }
        finally
        {
            lfbis.close();
        }

        assertFalse(cacheFile.exists());
    }

    @Test
    public void testCompleteExample() throws IOException
    {
        final int take = 6;
        final int bufferSize = 10;
        assertTrue(bufferSize < 2 * take);

        File folder = this.tempFolder.newFolder();
        assertEquals(0, folder.list().length);

        File cacheFile;
        LazyFileBufferedInputStream lfbis = new LazyFileBufferedInputStream(this.byteStream, bufferSize, folder);
        try
        {
            lfbis.mark(Integer.MAX_VALUE);

            byte[] readBuffer = new byte[2 * take];
            System.arraycopy(this.reader.take(take, lfbis), 0, readBuffer, 0, take);

            // assert file not yet written because it fits into memory buffer
            assertNull(lfbis.getCacheFile());
            assertEquals(0, folder.list().length);

            // take another chunk
            System.arraycopy(this.reader.take(take, lfbis), 0, readBuffer, take, take);

            // this time the file has been created
            cacheFile = lfbis.getCacheFile();
            assertNotNull(cacheFile);
            assertThat(cacheFile.getCanonicalPath(),
                    CoreMatchers.startsWith(folder.getCanonicalPath()));

            // file is created in given directory
            lfbis.flush();
            assertEquals(1, folder.list().length);

            // file contents must match the previously read bytes after flush
            FileInputStream fis = new FileInputStream(cacheFile);
            try
            {
                assertArrayEquals(readBuffer, this.reader.drain(fis));
            }
            finally
            {
                fis.close();
            }

            // check read and reset still works
            this.reader.drain(lfbis);
            lfbis.reset();

            assertArrayEquals(this.origBytes, this.reader.drain(lfbis));
        }
        finally
        {
            lfbis.close();
        }

        // after stream is closed, the file is removed
        assertFalse(cacheFile.exists());
        assertEquals(0, folder.list().length);
    }

    @Test
    public void testHelper_drain() throws IOException
    {
        assertArrayEquals(this.origBytes, this.reader.drain(this.byteStream));
    }

}
