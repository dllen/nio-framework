package cn.net.scp.nio.transform;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A Forwarder that prefixes a ByteBuffer array with another ByteBuffer.
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class ByteBufferArrayPrefixForwarder extends AbstractPrefixForwarder<ByteBuffer[], ByteBuffer[]> {

    private static final Logger logger = LogManager.getLogger();

    /**
     * creates a new ByteBufferArrayPrefixForwarder
     * @param prefixByteBufferForwardingMode the buffer forwarding mode for the
     * prefix ByteBuffer
     */
    public ByteBufferArrayPrefixForwarder(ByteBufferForwardingMode prefixByteBufferForwardingMode) {
        this.prefixByteBufferForwardingMode = prefixByteBufferForwardingMode;
    }

    @Override
    public synchronized void forward(ByteBuffer[] input) throws IOException {
        this.forward(prefix, input);
    }

    /**
     * creates a ByteBuffer[] of a prefix and input and forwards the array to
     * the next forwarder
     * @param prefix the prefix
     * @param input the input
     * @throws java.io.IOException if an I/O exception occurs
     */
    public synchronized void forward(ByteBuffer prefix, ByteBuffer[] input) throws IOException {
        if (nextForwarder == null) {
            logger.error("no nextForwarder => data lost!");
        } else {
            prefixCounter.addAndGet(prefix.remaining());
            int inputLength = input.length;
            ByteBuffer[] newArray = new ByteBuffer[inputLength + 1];
            newArray[0] = prefixByteBufferForwardingMode.getByteBuffer(prefix);
            System.arraycopy(input, 0, newArray, 1, inputLength);
            nextForwarder.forward(newArray);
            if (prefixByteBufferForwardingMode == ByteBufferForwardingMode.DIRECT) {
                prefix.rewind();
            }
        }
    }
}
