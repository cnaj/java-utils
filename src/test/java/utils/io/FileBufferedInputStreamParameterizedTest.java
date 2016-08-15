package utils.io;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import utils.io.testutils.BufferStreamReaderStrategy;
import utils.io.testutils.BytewiseStreamReaderStrategy;
import utils.io.testutils.StreamReaderStrategy;

@RunWith(Parameterized.class)
public class FileBufferedInputStreamParameterizedTest
{

    private static final StreamReaderStrategy BYTEWISE_READER = new BytewiseStreamReaderStrategy();
    private static final StreamReaderStrategy BUFFER_READER = new BufferStreamReaderStrategy();

    @Parameters(name = "{index}: s={0}")
    public static Iterable<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
                { BYTEWISE_READER },
                { BUFFER_READER },
        });
    }

    private StreamReaderStrategy reader;
    private int chunkSize;

    private ByteArrayInputStream byteStream;
    private byte[] origBytes;

    private FileBufferedInputStream fbis;

    public FileBufferedInputStreamParameterizedTest(StreamReaderStrategy reader)
    {
        this.reader = reader;
        this.chunkSize = 12;
    }

    @Before
    public void before() throws IOException
    {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = (byte) (i - 128);
        }
        this.origBytes = data;
        this.byteStream = new ByteArrayInputStream(this.origBytes);

        this.fbis = new FileBufferedInputStream(this.byteStream, null);
    }

    @After
    public void after() throws IOException
    {
        if (this.fbis != null)
        {
            this.fbis.close();
        }
    }

    @Test
    public void testReadSimple() throws IOException
    {
        assertArrayEquals(this.origBytes, this.reader.drain(this.fbis));
    }

    @Test
    public void testReset() throws IOException
    {
        assertArrayEquals(this.origBytes, this.reader.drain(this.fbis));

        this.fbis.reset();

        assertArrayEquals(this.origBytes, this.reader.drain(this.fbis));
    }

    @Test
    public void testContinueReadAfterReset() throws IOException
    {
        this.reader.take(this.chunkSize, this.fbis);

        this.fbis.reset();
        this.reader.take(this.chunkSize / 2, this.fbis);

        this.fbis.reset();
        assertArrayEquals(this.origBytes, this.reader.drain(this.fbis));
    }

    @Test
    public void testContinueReadAfterFlush() throws IOException
    {
        byte[] expected = Arrays.copyOfRange(this.origBytes, this.chunkSize, this.origBytes.length);

        this.reader.take(this.chunkSize, this.fbis);

        this.fbis.flush();

        assertArrayEquals(expected, this.reader.drain(this.fbis));
    }

    @Test
    public void testFlushAfterReset() throws IOException
    {
        this.reader.take(this.chunkSize, this.fbis);
        this.fbis.reset();

        this.fbis.flush();

        assertArrayEquals(this.origBytes, this.reader.drain(this.fbis));
    }

    @Test
    public void testFileCacheContents() throws IOException
    {
        byte[] expected = this.reader.drain(this.fbis);
        this.fbis.flush();

        FileInputStream fis = new FileInputStream(this.fbis.getCacheFile());
        try
        {
            // expected file contents: all bytes from mark position to last read position
            assertArrayEquals(expected, this.reader.drain(fis));
        }
        finally
        {
            fis.close();
        }
    }

}
