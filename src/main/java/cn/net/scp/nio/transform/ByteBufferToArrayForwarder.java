package cn.net.scp.nio.transform;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A forwarder that forwards a ByteBuffer as a ByteBuffer array holding this one
 * ByteBuffer.
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class ByteBufferToArrayForwarder
    extends AbstractForwarder<ByteBuffer, ByteBuffer[]> {

    private static final Logger logger = LogManager.getLogger();
    private static final ByteBuffer[] array = new ByteBuffer[1];

    @Override
    public synchronized void forward(ByteBuffer input) throws IOException {
        if (nextForwarder == null) {
            logger.error("no nextForwarder => data lost!");
        } else {
            array[0] = input;
            nextForwarder.forward(array);
        }
    }
}
