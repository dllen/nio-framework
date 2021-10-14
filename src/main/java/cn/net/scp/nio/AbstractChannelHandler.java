package cn.net.scp.nio;

import cn.net.scp.nio.transform.ChannelReader;
import cn.net.scp.nio.transform.ChannelWriter;

/**
 * Channel read & write for client & server
 */
public abstract class AbstractChannelHandler implements ChannelHandler {

    /**
     * the ChannelReader for this ChannelHandler
     */
    protected final ChannelReader reader;
    /**
     * the ChannelWriter for this ChannelHandler
     */
    protected final ChannelWriter writer;
    /**
     * the reference to the HandlerAdapter
     */
    protected HandlerAdapter handlerAdapter;

    /**
     *
     */
    public AbstractChannelHandler() {
        reader = new ChannelReader(false/*direct*/, 1024/*initial capacity*/, 10240/*max capacity*/);
        writer = new ChannelWriter(false/*direct*/);
    }


    public AbstractChannelHandler(boolean directReading, int initialReadingCapacity, int maxReadingCapacity, boolean directWriting) {
        reader = new ChannelReader(directReading, initialReadingCapacity, maxReadingCapacity);
        writer = new ChannelWriter(directWriting);
    }

    @Override
    public void channelRegistered(HandlerAdapter handlerAdapter) {
        this.handlerAdapter = handlerAdapter;
    }

    @Override
    public ChannelReader getChannelReader() {
        return reader;
    }

    @Override
    public ChannelWriter getChannelWriter() {
        return writer;
    }
}
