package cn.net.scp.nio.transform;

import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A Forwarder that unframes messages by removing a length header
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class FramingInputForwarder extends AbstractForwarder<ByteBuffer, ByteBuffer> {

    static final Logger logger = LogManager.getLogger();

    private final int HEADER_SIZE;
    private ByteBuffer buffer;
    private boolean determineLength;
    private int currentLength;
    private final AtomicLong headerCounter = new AtomicLong();
    private final AtomicLong dataCounter = new AtomicLong();

    /**
     * creates a new FramingInputForwarder
     * @param headerSize the size of the length header
     */
    public FramingInputForwarder(int headerSize) {
        HEADER_SIZE = headerSize;
        determineLength = true;
    }

    @Override
    public synchronized void forward(ByteBuffer input) throws IOException {
        if (nextForwarder == null) {
            logger.error("no nextForwarder => data lost!");
            return;
        }

        if ((buffer != null) && (buffer.hasRemaining())) {
            logger.trace("appending input to buffer");
            buffer = Tools.append(false/*direct*/, buffer, input);
            unframe(buffer);
        } else {
            logger.trace("unframing the input buffer directly: " + input);
            unframe(input);
            if (input.hasRemaining()) {
                buffer = Tools.append(false/*direct*/, buffer, input);
            }
        }
    }

    /**
     * returns how much header bytes have been filtered out
     * @return how much header bytes have been filtered out
     */
    public long getHeaderCounter() {
        return headerCounter.get();
    }

    /**
     * resets the header counter back to zero and returns how much header bytes
     * have been filtered out
     * @return how much header bytes have been filtered out
     */
    public long getAndResetHeaderCounter() {
        return headerCounter.getAndSet(0);
    }

    /**
     * returns how much data has been forwarded
     * @return how much data has been forwarded
     */
    public long getDataCounter() {
        return dataCounter.get();
    }

    /**
     * resets the data counter back to zero and returns how much data has been
     * forwarded
     * @return how much data has been forwarded
     */
    public long getAndResetDataCounter() {
        return dataCounter.getAndSet(0);
    }

    private void unframe(ByteBuffer buffer) throws IOException {
        while (setFrameBorders(buffer)) {
            ByteBufferForwardingMode.DIRECT.forwardBufferHead(buffer, currentLength, nextForwarder);
        }
    }

    private boolean setFrameBorders(ByteBuffer buffer) {
        if (determineLength) {
            int remaining = buffer.remaining();
            if (remaining < HEADER_SIZE) {
                logger.trace("incomplete header (header is " + HEADER_SIZE + " byte but only " + remaining + " bytes are currently available)");
            } else {
                // read and evaluate the length
                currentLength = 0;
                int shift = (HEADER_SIZE - 1) * 8;
                for (int i = 0; i < HEADER_SIZE; i++) {
                    currentLength |= ((buffer.get() & 0xFF) << shift);
                    shift -= 8;
                }
                logger.trace("currentLength = " + currentLength + " byte");
                determineLength = false;

                // accounting
                headerCounter.addAndGet(HEADER_SIZE);
            }
        }

        if (!determineLength) {
            int remaining = buffer.remaining();
            if (remaining < currentLength) {
                logger.trace("incomplete message (message is " + currentLength + " byte but only " + remaining + " bytes are currently available)");
            } else {
                determineLength = true;
                // accounting
                dataCounter.addAndGet(currentLength);
                return true;
            }
        }
        return false;
    }
}
