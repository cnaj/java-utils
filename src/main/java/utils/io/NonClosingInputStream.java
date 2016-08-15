package utils.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps an {@link InputStream}, delegating all methods except {@link #close()}
 * to the original stream. Instead of closing the original stream, it will be
 * disassociated with this stream.
 * <p>
 * This class is meant to be used with streams supporting
 * {@link InputStream#mark(int)} and {@link InputStream#reset()}, when methods
 * need to be called that always close the stream. Using this class, the
 * original stream can be used further.
 */
public class NonClosingInputStream extends FilterInputStream
{
    protected NonClosingInputStream(InputStream in)
    {
        super(in);
    }

    @Override
    public void close() throws IOException
    {
        // don't close the parent stream but disallow access to it
        this.in = null;
    }
}
