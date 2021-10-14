package cn.net.scp.nio.transform;

import cn.net.scp.nio.BufferListener;
import cn.net.scp.nio.BufferListenerHandler;
import cn.net.scp.nio.HandlerAdapter;
import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChannelWriter extends AbstractForwarder<ByteBuffer, Void> {

    static final Logger logger = LogManager.getLogger();

    protected final AtomicLong counter;
    private final boolean direct;
    private final BufferListenerHandler bufferListenerHandler;

    protected WritableByteChannel channel;
    protected ByteBuffer buffer;

    private HandlerAdapter handlerAdapter;

    public ChannelWriter(boolean direct) {
        this.direct = direct;
        this.counter = new AtomicLong(0);
        bufferListenerHandler = new BufferListenerHandler(this);
    }

    public synchronized void setChannel(WritableByteChannel channel) {
        this.channel = channel;
    }

    public synchronized void setHandlerAdapter(HandlerAdapter handlerAdapter) {
        this.handlerAdapter = handlerAdapter;
    }

    @Override
    public synchronized void forward(ByteBuffer input) throws IOException {
        logger.debug("input: " + input);
        if ((buffer == null) || !buffer.hasRemaining()) {
            // the buffer is empty
            // try writing the new data directly out the channel
            int bytesWritten = channel.write(input);
            counter.addAndGet(bytesWritten);
            logger.debug("bytesWritten = " + bytesWritten);
            if (input.hasRemaining()) {
                logger.debug("input.remaining() = " + input.remaining());
                buffer = Tools.append(direct, buffer, input);
                handlerAdapter.addInterestOps(SelectionKey.OP_WRITE);
            }
        } else {
            logger.debug("buffer.remaining() = " + buffer.remaining());
            buffer = Tools.append(direct, buffer, input);
        }
        detectFillLevelChanges();
    }

    @Override
    public synchronized void setNextForwarder(AbstractForwarder<Void, ?> nextForwarder) {
        throw new UnsupportedOperationException("ChannelWriter is always the last component of a forwarding hierarchy");
    }

    /**
     * writes cached data to the channel
     *
     * @return true, if draining was completed, false if there is remaining buffered data when returning
     * @throws java.io.IOException
     */
    public synchronized boolean drain() throws IOException {
        if (buffer == null) {
            return true;
        } else {
            if (buffer.hasRemaining()) {
                int bytesWritten = channel.write(buffer);
                counter.addAndGet(bytesWritten);
                logger.debug("bytesWritten = " + bytesWritten);
                detectFillLevelChanges();
            }
            return !buffer.hasRemaining();
        }
    }

    private void detectFillLevelChanges() {
        if (buffer == null) {
            logger.debug("buffer == null");
        } else {
            int newLevel = buffer.remaining();
            bufferListenerHandler.updateLevel(newLevel);
        }
    }

    /**
     * returns true, if there is unwritten data, false otherwise
     *
     * @return true, if there is unwritten data, false otherwise
     */
    public synchronized boolean hasRemaining() {
        return (buffer == null) ? false : buffer.hasRemaining();
    }

    /**
     * returns the number of unwritten bytes remaining in the buffer
     *
     * @return the number of unwritten bytes remaining in the buffer
     */
    public synchronized int remaining() {
        return (buffer == null) ? 0 : buffer.remaining();
    }

    /**
     * returns how many bytes have been written to the channel
     *
     * @return how many bytes have been written to the channel
     */
    public long getWriteCounter() {
        return counter.get();
    }

    /**
     * resets the counter back to zero and returns how many bytes have been written to the channel
     *
     * @return how many bytes have been written to the channel
     */
    public long getAndResetWriteCounter() {
        return counter.getAndSet(0);
    }

    /**
     * registers BufferSizeListener as event receiver.
     *
     * @param listener the listener to be registered
     */
    public synchronized void addBufferSizeListener(BufferListener listener) {
        bufferListenerHandler.addBufferListener(listener);
    }

    /**
     * removes a BufferSizeListener as event receiver.
     *
     * @param listener the listener to be removed
     */
    public synchronized void removeBufferSizeListener(BufferListener listener) {
        bufferListenerHandler.addBufferListener(listener);
    }
}
