package cn.net.scp.nio.transform;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StringToByteBufferConvertor extends AbstractConvertor<String, ByteBuffer> {

    static final Logger logger = LogManager.getLogger();
    private CharsetEncoder charsetEncoder;

    public StringToByteBufferConvertor() {
        this(Charset.defaultCharset());
    }

    public StringToByteBufferConvertor(Charset charset) {
        setCharsetPrivate(charset);
    }

    /**
     * sets the charset to use for transforming
     *
     * @param charset
     */
    public synchronized void setCharset(Charset charset) {
        setCharsetPrivate(charset);
    }

    private void setCharsetPrivate(Charset charset) {
        charsetEncoder = charset.newEncoder();
    }

    @Override
    public ByteBuffer convert(String input) throws ConvertException {
        logger.debug("convert : {}", input);
        CharBuffer charBuffer = CharBuffer.allocate(input.length());
        charBuffer.put(input);
        charBuffer.flip();
        try {
            return charsetEncoder.encode(charBuffer);
        } catch (CharacterCodingException ex) {
            throw new ConvertException(ex);
        }
    }

    @Override
    public void forward(String input) throws IOException {
        if (nextForwarder == null) {
            logger.info("next forwarder == null -> data lost!");
        } else {
            ByteBuffer byteBuffer;
            try {
                byteBuffer = convert(input);
                nextForwarder.forward(byteBuffer);
            } catch (ConvertException e) {
                throw new IOException(e);
            }
        }
    }
}
