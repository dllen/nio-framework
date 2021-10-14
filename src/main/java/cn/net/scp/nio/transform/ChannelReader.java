package cn.net.scp.nio.transform;

import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChannelReader extends AbstractForwarder<Void, ByteBuffer> {

    final static Logger logger = LogManager.getLogger();

    private final AtomicLong counter = new AtomicLong(0);

    private ReadableByteChannel channel;
    private ByteBuffer buffer;
    private volatile boolean closed;
    private final int maxCapacity;

    public ChannelReader(boolean direct, int initialCapacity, int maxCapacity) {
        this.maxCapacity = maxCapacity;
        if (direct) {
            buffer = ByteBuffer.allocateDirect(initialCapacity);
        } else {
            buffer = ByteBuffer.allocate(initialCapacity);
        }
        buffer.flip();
    }

    public synchronized void setChannel(SelectableChannel channel) {
        this.channel = (ReadableByteChannel) channel;
    }

    public synchronized boolean read() throws IOException {
        buffer.compact();
        int tmpCounter = 0;
        for (int bytesRead = 1; bytesRead > 0; ) {
            // check if there is still space left in the buffer
            if (!buffer.hasRemaining()) {
                // we must not grow larger than maxCapacity!
                int oldCapacity = buffer.capacity();
                if (oldCapacity < maxCapacity) {
                    // we need to enlarge the buffer ByteBuffer
                    // (try simple double size here)
                    int newCapacity = Math.min(oldCapacity * 2, maxCapacity);
                    buffer = Tools.enlargeBuffer(buffer, newCapacity);
                } else {
                    logger.debug("can not enlarge buffer, it already reached maxCapacity!");
                }
            }
            bytesRead = channel.read(buffer);
            if (bytesRead == -1) {
                closed = true;
            } else {
                tmpCounter += bytesRead;
            }
        }
        buffer.flip();
        counter.addAndGet(tmpCounter);
        if (logger.isDebugEnabled()) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(tmpCounter);
            stringBuilder.append(" Bytes read, buffer: ");
            stringBuilder.append(buffer);
            if (nextForwarder == null) {
                stringBuilder.append("\n\tThere is no forwarder! Storing data here in ChannelReader...");
            } else {
                stringBuilder.append("\n\tnextForwarder: ");
                stringBuilder.append(nextForwarder.getClass().getName());
            }
            logger.debug(stringBuilder.toString());
        }
        forward();
        return tmpCounter > 0;
    }

    public void transform() throws IOException {
        if (buffer.hasRemaining()) {
            if (nextForwarder == null) {
                logger.warn("can not forward, nextForwarder is null");
            } else {
                logger.debug("calling nextForwarder.forward(" + buffer + ')');
                nextForwarder.forward(buffer);
            }
        } else {
            logger.debug("nothing to forward, buffer is empty");
        }
    }


    public synchronized void forward() throws IOException {
        if (buffer.hasRemaining()) {
            if (nextForwarder == null) {
                logger.warn("can not forward, nextForwarder is null");
            } else {
                logger.debug("calling nextForwarder.forward(" + buffer + ")");
                nextForwarder.forward(buffer);
            }
        } else {
            logger.info("nothing to forward, buffer is empty");
        }
    }

    @Override
    public void forward(Void input) {
        throw new UnsupportedOperationException("Not supported.");
    }

    public long getReadCounter() {
        return counter.get();
    }

    public long getAndResetReadCounter() {
        return counter.getAndSet(0);
    }

    public boolean isClosed() {
        return closed;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }
}
