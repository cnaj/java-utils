package utils.io.testutils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class BufferStreamReaderStrategy implements StreamReaderStrategy
{

    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private int bufferSize;

    public BufferStreamReaderStrategy()
    {
        this(DEFAULT_BUFFER_SIZE);
    }

    public BufferStreamReaderStrategy(int bufferSize)
    {
        this.bufferSize = bufferSize;
    }

    @Override
    public byte[] take(int count, InputStream is) throws IOException
    {
        byte[] buffer = new byte[count];
        int offset = 0;
        int remaining = count;
        int read;
        while (remaining != (read = is.read(buffer, offset, remaining)))
        {
            if (read == -1)
            {
                throw new IllegalStateException("Unexpected end of stream");
            }
            offset += read;
            remaining = count - offset;
        }
        return buffer;
    }

    @Override
    public byte[] drain(InputStream is) throws IOException
    {
        byte[] buffer = new byte[this.bufferSize];
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int read;
        while (-1 != (read = is.read(buffer)))
        {
            os.write(buffer, 0, read);
        }
        return os.toByteArray();
    }

    @Override
    public String toString()
    {
        return "buffer";
    }

}
