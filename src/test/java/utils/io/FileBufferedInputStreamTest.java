package utils.io;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.File;
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

public class FileBufferedInputStreamTest
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
        FileBufferedInputStream fbis = new FileBufferedInputStream(is, null);
        try
        {
            assertTrue(fbis.markSupported());
        }
        finally
        {
            fbis.close();
        }
    }

    @Test
    public void testWrappedStreamIsClosedOnClose() throws IOException
    {
        InputStream is = mock(InputStream.class);
        FileBufferedInputStream fbis = new FileBufferedInputStream(is, null);

        fbis.close();

        verify(is).close();
    }

    @Test
    public void testFileCreatedOnInstantiation() throws IOException
    {
        FileBufferedInputStream fbis = new FileBufferedInputStream(this.byteStream, null);
        try
        {
            File cacheFile = fbis.getCacheFile();

            assertNotNull(cacheFile);
            assertTrue(cacheFile.exists());
        }
        finally
        {
            fbis.close();
        }
    }

    @Test
    public void testFileCreatedInGivenDirectory() throws IOException
    {
        File folder = this.tempFolder.newFolder();
        FileBufferedInputStream fbis = new FileBufferedInputStream(this.byteStream, folder);
        try
        {
            File cacheFile = fbis.getCacheFile();

            assertNotNull(cacheFile);
            assertThat(cacheFile.getCanonicalPath(),
                    CoreMatchers.startsWith(folder.getCanonicalPath()));
        }
        finally
        {
            fbis.close();
        }
    }

    @Test
    public void testCacheFileIsRemovedOnClose() throws IOException
    {
        File folder = this.tempFolder.newFolder();
        assertEquals(0, folder.list().length);

        File cacheFile;
        FileBufferedInputStream fbis = new FileBufferedInputStream(this.byteStream, folder);
        try
        {
            this.reader.drain(fbis);
            fbis.flush();
            cacheFile = fbis.getCacheFile();

            assertNotNull(cacheFile);
            assertTrue(cacheFile.exists());
            assertEquals(1, folder.list().length);
        }
        finally
        {
            fbis.close();
        }

        assertFalse(cacheFile.exists());
        assertEquals(0, folder.list().length);
    }

    @Test
    public void testResetWorksAfterInstantiation() throws IOException
    {
        InputStream is = new ByteArrayInputStream(new byte[] { 27, 1, 2, 3 });
        FileBufferedInputStream fbis = new FileBufferedInputStream(is, null);
        try
        {
            fbis.read();

            fbis.reset();

            assertEquals(27, fbis.read());
        }
        finally
        {
            fbis.close();
        }
    }

    @Test
    public void testMarkWorksAfterInstantiation() throws IOException
    {
        InputStream is = new ByteArrayInputStream(new byte[] { 27, 1, 2, 3 });
        FileBufferedInputStream fbis = new FileBufferedInputStream(is, null);
        try
        {
            fbis.mark(Integer.MAX_VALUE);

            fbis.read();

            fbis.reset();

            assertEquals(27, fbis.read());
        }
        finally
        {
            fbis.close();
        }
    }

    @Test
    public void testResetAfterCloseFails() throws IOException
    {
        FileBufferedInputStream fbis = new FileBufferedInputStream(mock(InputStream.class), null);
        try
        {
            fbis.mark(Integer.MAX_VALUE);
        }
        finally
        {
            fbis.close();
        }

        this.thrown.expect(IOException.class);
        fbis.reset();
    }

    @Test
    public void testNonClosingInputStream() throws IOException
    {
        FileBufferedInputStream fbis = new FileBufferedInputStream(this.byteStream, null);
        try
        {
            InputStream child1 = new NonClosingInputStream(fbis);
            try
            {
                assertArrayEquals(this.origBytes, this.reader.drain(child1));
            }
            finally
            {
                child1.close();
            }

            fbis.reset();
            InputStream child2 = new NonClosingInputStream(fbis);
            try
            {
                assertArrayEquals(this.origBytes, this.reader.drain(child2));
            }
            finally
            {
                child2.close();
            }

            fbis.reset();
            assertArrayEquals(this.origBytes, this.reader.drain(fbis));
        }
        finally
        {
            fbis.close();
        }
    }

    @Test
    public void testHelper_drain() throws IOException
    {
        assertArrayEquals(this.origBytes, this.reader.drain(this.byteStream));
    }

}
