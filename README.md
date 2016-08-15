# Miscellaneous Java utilities and classes

This repository holds some (hopefully) useful Java utilities and
classes. They are the result of coding practice and/or
curiosity. While trying to be thorough in design and unit testing,
take care to evaluate fitness for your purpose yourself.

Bug reports / pull requests are welcome!

## `utils.io`

### `FileBufferedInputStream`

A buffered `InputStream` that uses a temporary file to store another
stream's contents for replay. In contrast to `BufferedInputStream` you
don't risk running into out-of-memory conditions.

This class supports `InputStream.reset()` to replay the original
stream's contents from the beginning.

### `NonClosingInputStream`

Delegates all `InputStream` methods to another stream, except
`close()`. This class serves as a workaround when you want to reuse a
stream by using `reset()` with methods that take a stream parameter
that they close.

### `LazyFileBufferedInputStream`

A file and memory backed buffered input stream that will only create
the file when its memory buffer is full. This class fully supports
`mark()` and `reset()`.
