package cn.net.scp.nio.transform;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ByteBufferToStringConvertor extends AbstractConvertor<ByteBuffer, String> {

    static final Logger logger = LogManager.getLogger();

    private CharsetDecoder charsetDecoder;

    public ByteBufferToStringConvertor() {
        this(Charset.defaultCharset());
    }

    public ByteBufferToStringConvertor(Charset charset) {
        setCharsetPrivate(charset);
    }

    public synchronized void setCharset(Charset charset) {
        setCharsetPrivate(charset);
    }

    private void setCharsetPrivate(Charset charset) {
        charsetDecoder = charset.newDecoder();
    }


    @Override
    public String convert(ByteBuffer input) throws ConvertException {
        logger.debug("convert {}", input);
        String str;
        try {
            CharBuffer charBuffer = charsetDecoder.decode(input);
            str = charBuffer.toString();
            logger.debug("convert string: {}", str);
        } catch (Exception e) {
            throw new ConvertException(e);
        }
        return str;
    }

    @Override
    public void forward(ByteBuffer input) throws IOException {
        if (nextForwarder == null) {
            logger.info("no next forwarder => data lost!");
        } else {
            String msg;
            try {
                msg = convert(input);
                nextForwarder.forward(msg);
            } catch (ConvertException e) {
                throw new IOException(e);
            }
        }
    }
}
