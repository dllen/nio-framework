package cn.net.scp.nio;

import cn.net.scp.nio.transform.ChannelWriter;
import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Dispatcher extends Thread {

    static final Logger logger = LogManager.getLogger();

    private final Selector selector;
    private final ThreadFactory threadFactory;
    private final ScheduledExecutorService scheduledExecutorService;

    private Executor executor;

    public Dispatcher() throws IOException {
        setDaemon(true);
        selector = Selector.open();
        threadFactory = new CustomThreadFactory();
        scheduledExecutorService = Executors.newScheduledThreadPool(1, threadFactory);
    }

    public synchronized void setExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void run() {
        synchronized (this) {
            if (executor == null) {
                executor = Executors.newCachedThreadPool(threadFactory);
            }
            notifyAll();
        }
        while (true) {
            try {
                int updatedKeys = selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                logger.debug(updatedKeys + " keys updated " + selectedKeys.size() + " keys in selector's selected key set");
                if (!selectedKeys.isEmpty()) {
                    for (SelectionKey selectedKey : selectedKeys) {
                        Object attachment = selectedKey.attachment();
                        if (attachment instanceof HandlerAdapter) {
                            // run adapter in executor
                            HandlerAdapter adapter = (HandlerAdapter) attachment;
                            try {
                                adapter.cacheOps();
                                executor.execute(adapter);
                            } catch (CancelledKeyException ckException) {
                                // This may happen if another thread cancelled
                                // the selection key after we returned from
                                // select()
                                Tools.handleStackTrace(logger, ckException);
                            }
                        } else {
                            logger.warn("attachment is no HandlerAdapter: " + attachment);
                        }
                    }
                }
                selectedKeys.clear();
            } catch (Exception e) {
                Tools.handleStackTrace(logger, e);
                break;
            }
        }
    }

    /**
     * registers a channel at the dispatcher with {@link SelectionKey#OP_READ SelectionKey.OP_READ}
     *
     * @param channel        the channel to register
     * @param channelHandler an ChannelHandler for this channel
     * @throws java.nio.channels.ClosedChannelException if the channel to register is already closed
     */
    public void registerChannel(SelectableChannel channel, ChannelHandler channelHandler) throws ClosedChannelException {
        registerChannel(channel, channelHandler, SelectionKey.OP_READ);
    }

    /**
     * registers a channel at the dispatcher
     *
     * @param channel        the channel to register
     * @param channelHandler an ChannelHandler for this channel
     * @param interestOps    the interest ops to start with
     * @throws java.nio.channels.ClosedChannelException if the channel to register is already closed
     */
    public synchronized void registerChannel(SelectableChannel channel, ChannelHandler channelHandler, int interestOps) throws ClosedChannelException {
        selector.wakeup();
        // register
        SelectionKey key = channel.register(selector, interestOps);
        //
        HandlerAdapter handlerAdapter = new HandlerAdapter(this, channelHandler, key, channelHandler.getClass().getName());
        key.attach(handlerAdapter);
        channelHandler.getChannelReader().setChannel(channel);

        if (channel instanceof WritableByteChannel) {
            WritableByteChannel writableByteChannel = (WritableByteChannel) channel;
            ChannelWriter channelWriter = channelHandler.getChannelWriter();
            channelWriter.setChannel(writableByteChannel);
            channelWriter.setHandlerAdapter(handlerAdapter);
        }

        channelHandler.channelRegistered(handlerAdapter);
    }


    public synchronized void registerClientSocketChannelHandler(String host, int port, ClientSocketChannelHandler clientSocketChannelHandler) {
        registerClientSocketChannelHandler(host, port, clientSocketChannelHandler, 0);
    }

    public synchronized void registerClientSocketChannelHandler(String host, int port, ClientSocketChannelHandler clientSocketChannelHandler, int timeout) {
        //init executor
        while (executor == null) {
            try {
                wait();
            } catch (InterruptedException ex) {
                Tools.handleStackTrace(logger, ex);
            }
        }
        executor.execute(new Resolver(host, port, clientSocketChannelHandler, timeout));
    }

    /**
     * removes interest ops from a SelectionKey
     *
     * @param key         the SelectionKey
     * @param interestOps the interest ops to remove
     */
    public synchronized void removeInterestOps(
        SelectionKey key, int interestOps) {
        selector.wakeup();
        if (key.isValid()) {
            int newOps = key.interestOps() & ~interestOps;
            logger.debug("set interestOps to " + HandlerAdapter.interestToString(newOps));
            key.interestOps(newOps);
        } else {
            logger.warn("key is invalid");
        }
    }

    /**
     * sets the interest ops of a selection key
     *
     * @param key         the selection key of the channel
     * @param interestOps the interestOps to use when resuming the selection
     */
    public synchronized void setInterestOps(SelectionKey key, int interestOps) {
        selector.wakeup();
        if (key.isValid()) {
            logger.debug("set interestOps to " + HandlerAdapter.interestToString(interestOps));
            key.interestOps(interestOps);
        } else {
            logger.warn("key is invalid");
        }
    }

    /**
     * returns the interest ops of a SelectionKey without blocking
     *
     * @param key the selection key
     * @return the interest ops of the selection key
     */
    public synchronized int getInterestOps(SelectionKey key) {
        selector.wakeup();
        return key.interestOps();
    }


    public synchronized void closeChannel(SelectionKey selectionKey) throws IOException {
        selector.wakeup();
        selectionKey.cancel();
        selectionKey.attach(null);
        selectionKey.channel().close();
    }


    private class Resolver implements Runnable {

        private final String hostName;
        private final int port;
        private final ClientSocketChannelHandler clientSocketChannelHandler;
        private final int timeout;

        public Resolver(String hostName, int port,
            ClientSocketChannelHandler clientSocketChannelHandler,
            int timeout) {
            this.hostName = hostName;
            this.port = port;
            this.clientSocketChannelHandler = clientSocketChannelHandler;
            this.timeout = timeout;
        }

        @Override
        public void run() {
            InetSocketAddress address = new InetSocketAddress(hostName, port);
            if (address.isUnresolved()) {
                clientSocketChannelHandler.resolveFailed();
            } else {
                try {
                    SocketChannel socketChannel = SocketChannel.open();
                    socketChannel.configureBlocking(false);
                    boolean connected = socketChannel.connect(address);
                    if (connected) {
                        registerChannel(socketChannel, clientSocketChannelHandler, SelectionKey.OP_READ);
                        clientSocketChannelHandler.connectSucceeded();
                    } else {
                        registerChannel(socketChannel, clientSocketChannelHandler, SelectionKey.OP_CONNECT);
                        if (timeout > 0) {
                            TimeoutHandler timeoutHandler = new TimeoutHandler(socketChannel, clientSocketChannelHandler);
                            scheduledExecutorService.schedule(timeoutHandler, timeout, TimeUnit.MILLISECONDS);
                        }
                    }
                } catch (IOException ex) {
                    Tools.handleStackTrace(logger, ex);
                    clientSocketChannelHandler.connectFailed(ex);
                }
            }
        }
    }

    private static class TimeoutHandler implements Runnable {

        // use weak references so that the objects can be garbage collected
        private final WeakReference<SocketChannel> socketChannelReference;
        private final WeakReference<ClientSocketChannelHandler> handlerReference;

        public TimeoutHandler(SocketChannel socketChannel,
            ClientSocketChannelHandler clientSocketChannelHandler) {
            socketChannelReference = new WeakReference<>(socketChannel);
            handlerReference = new WeakReference<>(clientSocketChannelHandler);
        }

        @Override
        public void run() {
            // The HandlerAdapter may call socketChannel.finishConnect()
            // at the same time in another thread.
            // We synchronize both threads on the intrinsic lock of the
            // socketChannel.
            SocketChannel socketChannel = socketChannelReference.get();
            ClientSocketChannelHandler handler = handlerReference.get();
            if ((socketChannel != null) && (handler != null)) {
                synchronized (socketChannel) {
                    if (socketChannel.isConnectionPending()) {
                        try {
                            socketChannel.close();
                            handler.connectFailed(new ConnectException("Connection timeout"));
                        } catch (IOException ex) {
                            Tools.handleStackTrace(logger, ex);
                        }
                    }
                }
            }
        }
    }
}
