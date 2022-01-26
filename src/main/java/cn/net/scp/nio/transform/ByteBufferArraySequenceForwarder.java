package cn.net.scp.nio.transform;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A forwarder that forwards an array of ByteBuffers as a sequence of
 * ByteBuffers.
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class ByteBufferArraySequenceForwarder extends AbstractForwarder<ByteBuffer[], ByteBuffer> {

    private static final Logger logger = LogManager.getLogger();

    @Override
    public synchronized void forward(ByteBuffer[] inputs) throws IOException {
        if (nextForwarder == null) {
            logger.error("no nextForwarder => data lost!");
        } else {
            for (ByteBuffer input : inputs) {
                nextForwarder.forward(input);
            }
        }
    }
}
