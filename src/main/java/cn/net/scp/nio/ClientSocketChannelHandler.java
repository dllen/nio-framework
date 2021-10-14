package cn.net.scp.nio;

import java.io.IOException;

public interface ClientSocketChannelHandler extends ChannelHandler {

    /**
     * called by the framework if resolving the host name failed
     */
    void resolveFailed();

    /**
     * Called by the framework if connecting to the given host succeeded. WARNING: Do not use blocking calls within this method or handling of the connection by the NIO Framework will be stalled.
     */
    void connectSucceeded();

    /**
     * called by the framework if connecting to the given host failed
     *
     * @param exception the exception that occurred when the connection failed
     */
    void connectFailed(IOException exception);
}
