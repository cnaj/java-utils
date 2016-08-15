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
public class LazyFileBufferedInputStreamParameterizedTest
{

    private static final StreamReaderStrategy BYTEWISE_READER = new BytewiseStreamReaderStrategy();
    private static final StreamReaderStrategy BUFFER_READER = new BufferStreamReaderStrategy();

    @Parameters(name = "{index}: s={0}, bs={1}, cs={2}")
    public static Iterable<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
                { BYTEWISE_READER, 1024, 12 }, // default buffer size
                { BYTEWISE_READER, 67, 12 }, // buffer smaller that data but bigger than chunk
                { BYTEWISE_READER, 5, 12 }, // buffer smaller than chunk
                { BUFFER_READER, 1024, 12 }, // default buffer size
                { BUFFER_READER, 67, 12 }, // buffer smaller that data but bigger than chunk
                { BUFFER_READER, 5, 12 }, // buffer smaller than chunk
        });
    }

    private StreamReaderStrategy reader;
    private int bufferSize;
    private int chunkSize;

    private ByteArrayInputStream byteStream;
    private byte[] origBytes;

    private LazyFileBufferedInputStream lfbis;

    public LazyFileBufferedInputStreamParameterizedTest(StreamReaderStrategy reader, int bufferSize, int chunkSize)
    {
        this.reader = reader;
        this.bufferSize = bufferSize;
        this.chunkSize = chunkSize;
    }

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

        this.lfbis = new LazyFileBufferedInputStream(this.byteStream, this.bufferSize, null);
    }

    @After
    public void after() throws IOException
    {
        if (this.lfbis != null)
        {
            this.lfbis.close();
        }
    }

    @Test
    public void testReadSimple() throws IOException
    {
        assertArrayEquals(this.origBytes, this.reader.drain(this.lfbis));
    }

    @Test
    public void testMarkAndReset() throws IOException
    {
        this.lfbis.mark(Integer.MAX_VALUE);

        assertArrayEquals(this.origBytes, this.reader.drain(this.lfbis));

        this.lfbis.reset();

        assertArrayEquals(this.origBytes, this.reader.drain(this.lfbis));
    }

    @Test
    public void testMarkAndResetAfterRead() throws IOException
    {
        byte[] expectedFirst = Arrays.copyOf(this.origBytes, this.chunkSize);
        byte[] expected = Arrays.copyOfRange(this.origBytes, this.chunkSize, this.origBytes.length);

        assertArrayEquals(expectedFirst, this.reader.take(this.chunkSize, this.lfbis));
        this.lfbis.mark(Integer.MAX_VALUE);

        assertArrayEquals(expected, this.reader.drain(this.lfbis));

        this.lfbis.reset();

        assertArrayEquals(expected, this.reader.drain(this.lfbis));
    }

    @Test
    public void testMarkReadResetMarkRead() throws IOException
    {
        this.lfbis.mark(Integer.MAX_VALUE);
        this.reader.drain(this.lfbis);

        this.lfbis.reset();
        this.lfbis.mark(Integer.MAX_VALUE);

        assertArrayEquals(this.origBytes, this.reader.drain(this.lfbis));
    }

    @Test
    public void testMarkReadResetReadMarkAndRead() throws IOException
    {
        byte[] expected = Arrays.copyOfRange(this.origBytes, this.chunkSize, this.origBytes.length);

        this.lfbis.mark(Integer.MAX_VALUE);
        this.reader.drain(this.lfbis);

        this.lfbis.reset();

        this.reader.take(this.chunkSize, this.lfbis);
        this.lfbis.mark(Integer.MAX_VALUE);

        this.reader.drain(this.lfbis);
        this.lfbis.reset();

        assertArrayEquals(expected, this.reader.drain(this.lfbis));
    }

    @Test
    public void testContinueReadAfterReset() throws IOException
    {
        this.lfbis.mark(Integer.MAX_VALUE);
        this.reader.take(this.chunkSize, this.lfbis);

        this.lfbis.reset();
        this.reader.take(this.chunkSize / 2, this.lfbis);

        this.lfbis.reset();
        assertArrayEquals(this.origBytes, this.reader.drain(this.lfbis));
    }

    @Test
    public void testContinueReadAfterFlush() throws IOException
    {
        byte[] expected = Arrays.copyOfRange(this.origBytes, this.chunkSize, this.origBytes.length);

        this.lfbis.mark(Integer.MAX_VALUE);
        this.reader.take(this.chunkSize, this.lfbis);

        this.lfbis.flush();

        assertArrayEquals(expected, this.reader.drain(this.lfbis));
    }

    @Test
    public void testFlushAfterReset() throws IOException
    {
        this.lfbis.mark(Integer.MAX_VALUE);
        this.reader.take(this.chunkSize, this.lfbis);
        this.lfbis.reset();

        this.lfbis.flush();

        assertArrayEquals(this.origBytes, this.reader.drain(this.lfbis));
    }

    @Test
    public void testMultipleMark() throws IOException
    {
        byte[] expected = Arrays.copyOfRange(this.origBytes, this.chunkSize, this.origBytes.length);

        this.lfbis.mark(Integer.MAX_VALUE);
        this.reader.take(this.chunkSize, this.lfbis);

        this.lfbis.mark(Integer.MAX_VALUE);
        this.reader.take(this.chunkSize, this.lfbis);

        this.lfbis.reset();
        assertArrayEquals(expected, this.reader.drain(this.lfbis));
    }

    @Test
    public void testFileCacheContents() throws IOException
    {
        // throw away a chunk from the start and set mark
        this.reader.take(this.chunkSize, this.lfbis);
        this.lfbis.mark(Integer.MAX_VALUE);

        byte[] expected = this.reader.drain(this.lfbis);
        this.lfbis.flush();

        FileInputStream fis = new FileInputStream(this.lfbis.getCacheFile());
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
