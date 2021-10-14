package cn.net.scp.nio;

import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hold ServerSocketChannel and accept client conn
 */
public abstract class AbstractAcceptor extends Thread {

    static final Logger logger = LogManager.getLogger();

    protected final Dispatcher dispatcher;
    private final ServerSocketChannel serverSocketChannel;

    public AbstractAcceptor(Dispatcher dispatcher, SocketAddress socketAddress) throws IOException {
        super(AbstractAcceptor.class.getName());
        this.dispatcher = dispatcher;
        serverSocketChannel = ServerSocketChannel.open();
        ServerSocket serverSocket = serverSocketChannel.socket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(socketAddress);
        serverSocketChannel.configureBlocking(false);
    }

    @Override
    public void run() {
        while (true) {
            try {
                SocketChannel socketChannel = serverSocketChannel.accept();
                if (socketChannel != null) {
                    logger.debug("new connection accepted");
                    socketChannel.configureBlocking(false);
                    ChannelHandler handler = getHandler(socketChannel);
                    dispatcher.registerChannel(socketChannel, handler);
                }
            } catch (IOException ex) {
                Tools.handleStackTrace(logger, ex);
                break;
            }
        }
    }

    /**
     * stops the acceptor
     */
    public void stopAcceptor() {
        interrupt();
    }

    /**
     * Returns the appropriate ChannelHandler for the given SocketChannel.
     *
     * @param socketChannel the SocketChannel of a new connection
     * @return the appropriate ChannelHandler for the given SocketChannel
     */
    protected abstract ChannelHandler getHandler(SocketChannel socketChannel);
}
