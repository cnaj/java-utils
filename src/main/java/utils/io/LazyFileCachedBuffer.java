package utils.io;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A buffer for {@code byte} data that will be backed by a cache file if the
 * memory buffer should be too small for the amount of data to hold.
 * <p>
 * The buffer has a <em>writing</em> mode (which is active after creating the
 * instance) and a <em>reading</em> mode. You can switch to <em>reading</em>
 * mode by using {@link #rewind()} and to <em>writing</em> mode by writing data.
 * <p>
 * This class is <em>not</em> thread safe.
 */
public class LazyFileCachedBuffer implements Closeable
{

    private ByteBuffer memoryBuffer;
    private boolean reading;

    private File directory;
    private File cacheFile;
    private RandomAccessFile raFile;
    private long markPosition;

    /**
     * Allocates the memory buffer, but does not yet create the cache file.
     * 
     * @param memoryBufferSize
     *            the size of the memory buffer
     * @param directory
     *            the directory where the cache file should be created, or
     *            <code>null</code> if the system temporary directory should be
     *            used
     */
    public LazyFileCachedBuffer(int memoryBufferSize, File directory)
    {
        this.memoryBuffer = ByteBuffer.allocateDirect(memoryBufferSize);
        this.directory = directory;
    }

    /**
     * Closes and deletes the cache file if it has been created, and releases
     * the memory buffer.
     * 
     * @throws IOException
     *             if an IO error occurs.
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
        this.memoryBuffer = null;
    }

    /**
     * Reads a byte and returns it as a value between {@code 0 <= b <= 255}. If
     * no more data is available, {@code -1} is returned instead.
     * 
     * @return the next byte of data, or {@code -1} if no more data is
     *         available.
     * @throws IOException
     *             if a cache file has been created and an IO error occured
     *             reading the file
     * @throws IllegalStateException
     *             if the buffer is not in <em>reading</em> mode
     */
    public int read() throws IOException
    {
        if (!this.reading)
        {
            throw new IllegalStateException("buffer not in reading mode");
        }

        if (this.raFile != null)
        {
            int b = this.raFile.read();
            if (b >= 0)
            {
                return b;
            }
        }
        if (this.memoryBuffer.hasRemaining())
        {
            return 255 & this.memoryBuffer.get();
        }
        return -1;
    }

    /**
     * @param b
     *            the buffer where the data should be stored
     * @param off
     *            the offset in {@code b} where the data should start
     * @param len
     *            the maximum number of bytes to read
     * @return the number of bytes ({@code >= 0}) read into {@code b}.
     * @throws IOException
     *             if a cache file has been created and an IO error occured
     *             reading the file
     * @throws IndexOutOfBoundsException
     *             if {@code offset} is negative, {@code len} is negative, or
     *             {@code len} is greater than {@code b.length - off}
     * @throws IllegalStateException
     *             if the buffer is not in <em>reading</em> mode
     */
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
        if (!this.reading)
        {
            throw new IllegalStateException("buffer not in reading mode");
        }

        if (this.raFile != null)
        {
            int read = this.raFile.read(b, off, len);
            if (read >= 0)
            {
                // early return if we got any data
                return read;
            }
        }

        len = Math.min(len, this.memoryBuffer.remaining());
        if (len > 0)
        {
            this.memoryBuffer.get(b, off, len);
        }

        return len;
    }

    /**
     * Switches the buffer to <em>writing</em> mode if not already in it, and
     * writes the given data to the end of the buffer. If the memory buffer is
     * full, the cache file will be created if not already created, and the
     * contents of the buffer will be appended to the cache file.
     * 
     * @param b
     *            the byte to write to the buffer. Only the last 8 bit will be
     *            regarded, the first 24 bit will be ignored.
     * @throws IOException
     *             if the cache file could not be created or written to, or if
     *             an IO error occurs.
     */
    public void write(int b) throws IOException
    {
        finishReading();

        if (!this.memoryBuffer.hasRemaining())
        {
            flushCache();
        }
        this.memoryBuffer.put((byte) b);
    }

    /**
     * Switches the buffer to <em>writing</em> mode if not already in it, and
     * writes the given data to the end of the buffer. If the memory buffer is
     * full, the cache file will be created if not already created, and the
     * contents of the buffer will be appended to the cache file.
     * 
     * @param b
     *            the data to write to the buffer
     * @param off
     *            the start offset in the data
     * @param len
     *            the number of bytes to write
     * @throws IOException
     *             if the cache file could not be created or written to, or if
     *             an IO error occurs.
     */
    public void write(byte[] b, int off, int len) throws IOException
    {
        finishReading();

        if (len > this.memoryBuffer.remaining())
        {
            flushCache();
        }

        if (len > this.memoryBuffer.remaining())
        {
            // Data is bigger than cache, so write everything into file.

            // Buffer was emptied by previous flushCache operation.
            assert this.memoryBuffer.remaining() == this.memoryBuffer.capacity();

            // File has been created by previous flushCache operation.
            assert this.raFile != null;

            this.raFile.write(b, off, len);
            return;
        }

        this.memoryBuffer.put(b, off, len);
    }

    /**
     * @return {@code true} if the buffer is in <em>reading</em> mode,
     *         {@code false} if it is in <em>writing</em> mode.
     */
    public boolean isReading()
    {
        return this.reading;
    }

    /**
     * Marks the current position of the buffer so that a subsequent
     * {@link #rewind()} will start at this position.
     * 
     * @throws IOException
     *             if an IO error occurs.
     */
    public void mark() throws IOException
    {
        this.markPosition = currentPosition();
    }

    /**
     * Sets the buffer to <em>reading</em> mode and resets the position to the
     * last mark position.
     * 
     * @throws IOException
     *             if an IO error occurs
     */
    public void rewind() throws IOException
    {
        if (this.reading)
        {
            this.memoryBuffer.rewind();
        }
        else
        {
            this.memoryBuffer.flip();
            this.reading = true;
        }

        long fileLength = fileLength();
        long filePosition = Math.min(this.markPosition, fileLength);
        int memoryPosition = (int) Math.max(this.markPosition - fileLength, 0);

        fileSeek(filePosition);
        this.memoryBuffer.position(memoryPosition);
    }

    /**
     * @return the cache file, or <code>null</code> if no cache file has been
     *         created yet.
     */
    public File getCacheFile()
    {
        return this.cacheFile;
    }

    /**
     * Appends the contents of the memory buffer to the cache file, creating it
     * first if necessary. The data will be forces to the operating system, see
     * {@link FileChannel#force(boolean)}
     * 
     * @throws IOException
     *             if the cache file could not be created or written to, or if
     *             an IO error occurs.
     */
    public void flush() throws IOException
    {
        flushCache();
        this.raFile.getChannel().force(true);
    }

    private void flushCache() throws IOException
    {
        if (this.cacheFile == null)
        {
            this.cacheFile = File.createTempFile("lfcb-", null, this.directory);
            this.raFile = new RandomAccessFile(this.cacheFile, "rw");
        }

        long oldPosition = currentPosition();
        fileSeek(fileLength());

        ByteBuffer bufferView = this.memoryBuffer.asReadOnlyBuffer();
        bufferView.position(0);
        bufferView.limit(this.reading ? this.memoryBuffer.limit() : this.memoryBuffer.position());

        FileChannel channel = this.raFile.getChannel();
        channel.write(bufferView);

        this.memoryBuffer.clear();
        if (this.reading)
        {
            this.memoryBuffer.limit(0);
        }
        fileSeek(oldPosition);
    }

    private long currentPosition() throws IOException
    {
        return filePosition() + this.memoryBuffer.position();
    }

    private void finishReading() throws IOException
    {
        if (this.reading)
        {
            this.memoryBuffer.position(this.memoryBuffer.limit());
            this.memoryBuffer.limit(this.memoryBuffer.capacity());
            fileSeek(fileLength());
            this.reading = false;
        }
    }

    private long filePosition() throws IOException
    {
        if (this.raFile == null)
        {
            return 0L;
        }

        long filePointer = this.raFile.getFilePointer();
        return filePointer;
    }

    private long fileLength() throws IOException
    {
        if (this.raFile == null)
        {
            return 0L;
        }

        long length = this.raFile.length();
        return length;
    }

    private void fileSeek(long pos) throws IOException
    {
        if (this.raFile == null)
        {
            assert pos == 0;
            return;
        }

        this.raFile.seek(pos);
    }

}
