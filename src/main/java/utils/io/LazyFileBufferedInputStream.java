package utils.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A buffered {@link InputStream} backed by a file cache. The memory buffer will
 * not be reallocated automatically, thus giving control over memory usage. The
 * cache file will only be created when the mark is set and the buffer is not
 * big enough to hold all bytes read since the mark position, or when
 * {@link #flush()} is called.
 * <p>
 * This stream supports the defined behavior of {@link #mark(int)} and
 * {@link #reset()}, as defined in the {@link InputStream} documentation.
 */
public class LazyFileBufferedInputStream extends InputStream
{

    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private InputStream in;
    private File directory;

    private int memoryBufferSize;
    private LazyFileCachedBuffer buffer;

    /**
     * Creates a new instance with a default memory buffer size of
     * {@value #DEFAULT_BUFFER_SIZE} bytes.
     * 
     * @param in
     *            an {@link InputStream} which will be read. This stream will be
     *            closed when {@link #close()} is called.
     * @param directory
     *            the directory where the backing file will be created. May be
     *            {@code null}, in which case the system temporary directory
     *            will be used.
     */
    public LazyFileBufferedInputStream(InputStream in, File directory)
    {
        this(in, DEFAULT_BUFFER_SIZE, directory);
    }

    /**
     * @param in
     *            an {@link InputStream} which will be read. This stream will be
     *            closed when {@link #close()} is called.
     * @param memoryBufferSize
     *            the size of the memory buffer
     * @param directory
     *            the directory where the backing file will be created. May be
     *            {@code null}, in which case the system temporary directory
     *            will be used.
     */
    public LazyFileBufferedInputStream(InputStream in, int memoryBufferSize, File directory)
    {
        super();
        if (in == null)
        {
            throw new NullPointerException();
        }
        this.in = in;
        this.memoryBufferSize = memoryBufferSize;
        this.directory = directory;
    }

    /**
     * Closes the associated stream and system resources. If a cache file has
     * been created, it will be deleted.
     */
    @Override
    public void close() throws IOException
    {
        if (this.buffer != null)
        {
            this.buffer.close();
            this.buffer = null;
        }
        if (this.in != null)
        {
            this.in.close();
            this.in = null;
        }
    }

    @Override
    public int read() throws IOException
    {
        checkClosed();

        if (this.buffer == null)
        {
            return this.in.read();
        }

        if (this.buffer.isReading())
        {
            int b = this.buffer.read();
            if (b >= 0)
            {
                return b;
            }
        }

        int b = this.in.read();
        if (b == -1)
        {
            return -1;
        }
        this.buffer.write(b);

        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (b == null)
        {
            throw new NullPointerException();
        }
        else if (off < 0 || len < 0 || len > b.length - off)
        {
            throw new IndexOutOfBoundsException();
        }
        else if (len == 0)
        {
            return 0;
        }
        checkClosed();

        if (this.buffer == null)
        {
            return this.in.read(b, off, len);
        }

        int totalRead;
        if (this.buffer.isReading())
        {
            int read = this.buffer.read(b, off, len);
            if (read == len)
            {
                return read;
            }
            off += read;
            len -= read;
            totalRead = read;
        }
        else
        {
            totalRead = 0;
        }

        int read = this.in.read(b, off, len);
        if (read == -1)
        {
            return totalRead == 0 ? -1 : totalRead;
        }
        this.buffer.write(b, off, read);

        totalRead += read;
        return totalRead;
    }

    /**
     * Writes all previously read bytes (starting from the first time the mark
     * was set) into the cache file.
     * 
     * @throws IOException
     *             if mark was not set before, or if the cache file could not be
     *             created, or if an IO error occured.
     */
    public void flush() throws IOException
    {
        if (this.buffer == null)
        {
            throw new IOException("no mark was set");
        }
        this.buffer.flush();
    }

    /**
     * @return the cache file. Will be {@code null} if the file was not yet
     *         written.
     * @see #flush()
     */
    public File getCacheFile()
    {
        return this.buffer != null ? this.buffer.getCacheFile() : null;
    }

    /**
     * @param readlimit
     *            this parameter is ignored. For semantic consistency with other
     *            stream implementations, you should use
     *            {@link Integer#MAX_VALUE}.
     * @throws IllegalStateException
     *             if the cache file in use could not be cleared
     * @see InputStream#mark(int)
     */
    @Override
    public synchronized void mark(int readlimit)
    {
        if (this.in == null)
        {
            return;
        }
        if (this.buffer == null)
        {
            this.buffer = new LazyFileCachedBuffer(this.memoryBufferSize, this.directory);
        }

        try
        {
            this.buffer.mark();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("unexpected IO error", e);
        }
    }

    /**
     * @throws IOException
     *             if this stream has not been marked, or the stream has been
     *             closed by invoking its close() method, or an I/O error
     *             occurs.
     * @see InputStream#reset()
     */
    @Override
    public synchronized void reset() throws IOException
    {
        checkClosed();
        if (this.buffer == null)
        {
            throw new IOException("Mark has not been set");
        }
        this.buffer.rewind();
    }

    /**
     * Tests if this input stream supports the mark and reset methods. This
     * implementation returns {@code true}.
     * 
     * @return a boolean indicating if this stream type supports the mark and
     *         reset methods.
     * @see InputStream#mark(int)
     * @see InputStream#reset()
     */
    @Override
    public boolean markSupported()
    {
        return true;
    }

    private void checkClosed() throws IOException
    {
        if (this.in == null)
        {
            throw new IOException("Stream is closed");
        }
    }

}
