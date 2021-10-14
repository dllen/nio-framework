package cn.net.scp.nio.buffer;

import java.nio.ByteBuffer;

/**
 * Represents a pool of non-direct {@link ByteBuffer} objects.
 *
 * @author Jacob G.
 * @since February 23, 2019
 */
public final class ByteBufferPool extends AbstractBufferPool<ByteBuffer> {

    @Override
    protected ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code buffer} is direct.
     */
    @Override
    public void give(ByteBuffer buffer) {
        if (buffer.isDirect()) {
            throw new IllegalArgumentException("A direct ByteBuffer cannot be given to a ByteBufferPool!");
        }

        super.give(buffer);
    }
}
