package cn.net.scp.nio.transform;

import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An enum of all known ByteBuffer forwarding modes .
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public enum ByteBufferForwardingMode {

    /**
     * ByteBuffers are forwarded directly.
     */
    DIRECT,
    /**
     * Duplicates of ByteBuffers are forwarded. This is reasonable in situations
     * where the content of a ByteBuffer can be shared between many instants but
     * every instant needs a different position and limit.
     */
    DUPLICATE,
    /**
     * Copies of ByteBuffers are forwarded.
     */
    COPY;
    private static final Logger logger = LogManager.getLogger();

    /**
     * returns a reference to the byteBuffer according to the strategy
     * @param byteBuffer the ByteBuffer which reference has to be returned
     * according to the defined strategy
     * @return a reference to the byteBuffer according to the strategy
     */
    public ByteBuffer getByteBuffer(ByteBuffer byteBuffer) {
        switch (this) {
            case COPY:
                return Tools.copyBuffer(byteBuffer);
            case DIRECT:
                return byteBuffer;
            case DUPLICATE:
                return byteBuffer.duplicate();
            default:
                logger.info("unknown ByteBufferForwardingMode {}", this);
                return null;
        }
    }

    /**
     * forwards the head of a ByteBuffer
     * @param byteBuffer the ByteBuffer to forward
     * @param size the size of the head
     * @param forwarder the forwarder to use
     * @throws IOException if an I/O exception occurs while forwarding the head
     * of <tt>byteBuffer</tt>
     */
    public void forwardBufferHead(ByteBuffer byteBuffer, int size, AbstractForwarder<ByteBuffer, ?> forwarder) throws IOException {

        switch (this) {
            case COPY:
                forwarder.forward(Tools.copyBuffer(byteBuffer, size));
                break;

            case DIRECT:
                int oldLimit = byteBuffer.limit();
                int newLimit = byteBuffer.position() + size;
                byteBuffer.limit(newLimit);
                forwarder.forward(byteBuffer);
                // reset buffer
                if (byteBuffer.position() != newLimit) {
                    /**
                     * position() is much simpler than position(int newPosition)
                     * because correcting the position is only very seldomly
                     * necessary, we put the check here in front (only happens
                     * if next forwarders do not consume the message completely)
                     */
                    if (logger.isWarnEnabled()) {
                        logger.warn("buffer was not completely " + "consumed by nextForwarder: " + byteBuffer);
                    }
                    byteBuffer.position(newLimit);
                }
                byteBuffer.limit(oldLimit);
                break;

            case DUPLICATE:
                forwarder.forward(Tools.splitBuffer(byteBuffer, size));
                break;

            default:
                logger.info("unknown ByteBufferForwardingMode \"" + this + "\"");
        }
    }
}
