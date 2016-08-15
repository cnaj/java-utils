package utils.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * A buffered {@link InputStream} that uses a temporary file to store another
 * stream's contents for replay. This stream supports the {@link #reset()}
 * method, as defined in {@link InputStream}. Upon creation, the mark will
 * already be set, so that a subsequent {@link #reset()} will reset the stream
 * to the position it had when this instance was created. Any call to
 * {@link #mark(int)} after data has been read will lead to an exception.
 * <p>
 * This class is <em>not</em> thread safe.
 */
public class FileBufferedInputStream extends InputStream
{

    private InputStream in;
    private File cacheFile;
    private RandomAccessFile raFile;
    private boolean replaying;

    /**
     * Creates a new cache file to hold the given stream's contents and sets the
     * {@link #mark(int)}.
     * 
     * @param in
     *            the {@link InputStream} which will be read. This stream will
     *            be closed when {@link #close()} is called.
     * @param directory
     *            the directory where the cache file will be created. May be
     *            {@code null}, in which case the system temporary directory
     *            will be used.
     * @throws IOException
     *             if the cache file coud not be created
     */
    public FileBufferedInputStream(InputStream in, File directory) throws IOException
    {
        super();
        if (in == null)
        {
            throw new NullPointerException();
        }
        this.in = in;

        if (this.cacheFile == null)
        {
            this.cacheFile = File.createTempFile("fbis-", null, directory);
            this.raFile = new RandomAccessFile(this.cacheFile, "rw");
        }
    }

    /**
     * Deletes the cache file and closes the associated stream and system
     * resources.
     */
    @Override
    public void close() throws IOException
    {
        if (this.raFile != null)
        {
            this.raFile.close();
            this.raFile = null;
        }
        if (this.cacheFile != null)
        {
            if (!this.cacheFile.delete())
            {
                throw new IOException("Could not delete temporary file " + this.cacheFile);
            }
            this.cacheFile = null;
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

        if (this.replaying)
        {
            int b = this.raFile.read();
            if (b >= 0)
            {
                return b;
            }
            this.replaying = false;
        }

        int b = this.in.read();
        if (b == -1)
        {
            return -1;
        }
        this.raFile.write(b);

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

        if (this.replaying)
        {
            int read = this.raFile.read(b, off, len);
            if (read >= 0)
            {
                // early return if we got any data
                return read;
            }

            // no more data in file: read returned -1
            this.replaying = false;
        }

        int read = this.in.read(b, off, len);
        if (read == -1)
        {
            return -1;
        }

        this.raFile.write(b, off, read);
        return read;
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

    /**
     * This implementation only supports mark if no data has been read yet.
     * 
     * @param readlimit
     *            this parameter is ignored
     * @throws IllegalStateException
     *             thrown if the method is called after data has been read
     */
    @Override
    public synchronized void mark(int readlimit)
    {
        try
        {
            if (this.raFile.getFilePointer() > 0)
            {
                throw new IllegalStateException("mark can not be reset");
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("unexpected IO error", e);
        }
    }

    @Override
    public synchronized void reset() throws IOException
    {
        checkClosed();
        this.replaying = true;
        this.raFile.seek(0);
    }

    /**
     * Writes all previously read bytes (starting from mark position) into the
     * cache file.
     * 
     * @throws IOException
     *             if an IO error occured.
     */
    public void flush() throws IOException
    {
        this.raFile.getChannel().force(true);
    }

    /**
     * @return the cache file
     * @see #flush()
     */
    public File getCacheFile()
    {
        return this.cacheFile;
    }

    private void checkClosed() throws IOException
    {
        if (this.in == null)
        {
            throw new IOException("Stream is closed");
        }
    }

}
