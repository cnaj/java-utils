package utils.io.testutils;

import java.io.IOException;
import java.io.InputStream;

public interface StreamReaderStrategy
{

    byte[] take(int count, InputStream is) throws IOException;

    byte[] drain(InputStream is) throws IOException;

}
