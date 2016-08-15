package utils.io.testutils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class BytewiseStreamReaderStrategy implements StreamReaderStrategy
{

    public BytewiseStreamReaderStrategy()
    {
    }

    @Override
    public byte[] take(int count, InputStream is) throws IOException
    {
        byte[] buffer = new byte[count];
        for (int i = 0; i < buffer.length; i++)
        {
            int b = is.read();
            if (b < 0)
            {
                throw new IllegalStateException("Unexpected end of stream");
            }
            buffer[i] = (byte) b;
        }
        return buffer;
    }

    @Override
    public byte[] drain(InputStream is) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int b;
        while (-1 != (b = is.read()))
        {
            os.write(b);
        }
        return os.toByteArray();
    }

    @Override
    public String toString()
    {
        return "bytewise";
    }

}
