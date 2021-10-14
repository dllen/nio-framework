package cn.net.scp.nio;

import cn.net.scp.nio.transform.ChannelReader;
import cn.net.scp.nio.transform.ChannelWriter;
import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mix of ChannelHandler & channelReader & channelWriter & Dispatcher & SelectionKey
 */
public class HandlerAdapter implements Runnable {

    static final Logger logger = LogManager.getLogger();

    private final Dispatcher dispatcher;
    private final ChannelHandler channelHandler;
    private final ChannelWriter channelWriter;
    private final ChannelReader channelReader;

    private final SelectionKey selectionKey;
    private final String debugName;

    // @GuardedBy("this")
    private volatile int cachedInterestOps;
    // @GuardedBy("this")
    private volatile int cachedReadyOps;
    // @GuardedBy("this")
    private volatile boolean opsCached;


    public HandlerAdapter(Dispatcher dispatcher, ChannelHandler channelHandler, SelectionKey selectionKey, String debugName) {
        this.dispatcher = dispatcher;
        this.channelHandler = channelHandler;
        this.selectionKey = selectionKey;
        this.debugName = debugName;

        channelReader = channelHandler.getChannelReader();
        channelWriter = channelHandler.getChannelWriter();

        cachedInterestOps = selectionKey.interestOps();
    }

    @Override
    public void run() {
        logger.debug(debugName + " output handling");
        try {
            if ((cachedReadyOps & SelectionKey.OP_READ) != 0) {
                SelectableChannel channel = selectionKey.channel();
                if (!(channel instanceof SocketChannel)) {
                    throw new IllegalStateException("SelectionKey is connectable but channel is no SocketChannel!");
                }
                SocketChannel socketChannel = (SocketChannel) channel;
                synchronized (socketChannel) {
                    if (socketChannel.isOpen()) {
                        if (channelHandler instanceof ClientSocketChannelHandler) {
                            ClientSocketChannelHandler clientSocketChannelHandler = (ClientSocketChannelHandler) channelHandler;
                            try {
                                socketChannel.finishConnect();
                                cachedInterestOps = SelectionKey.OP_READ;
                                clientSocketChannelHandler.connectSucceeded();
                            } catch (IOException ex) {
                                cachedReadyOps = 0;
                                selectionKey.cancel();
                                clientSocketChannelHandler.connectFailed(ex);
                                clientSocketChannelHandler.channelException(ex);
                            }
                        } else {
                            try {
                                socketChannel.finishConnect();
                                cachedInterestOps = SelectionKey.OP_READ;
                            } catch (IOException ex) {
                                cachedReadyOps = 0;
                                selectionKey.cancel();
                                channelHandler.channelException(ex);
                            }
                        }
                    }
                }
            }
            if ((cachedReadyOps & SelectionKey.OP_WRITE) != 0) {
                if (channelWriter.drain()) {
                    removeInterestOps(SelectionKey.OP_WRITE);
                }
            }
            logger.debug(debugName + " input handling");
            if ((cachedReadyOps & SelectionKey.OP_READ) != 0) {
                channelReader.read();
                if (channelReader.isClosed()) {
                    logger.debug(debugName + " input closed -> removing read interest");
                    removeInterestOps(SelectionKey.OP_READ);
                    channelHandler.inputClosed();
                }
            }
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception e) {
            Tools.handleStackTrace(logger, e);
            try {
                closeChannel();
            } catch (Exception ex) {
                Tools.handleStackTrace(logger, ex);
            }
            channelHandler.channelException(e);
        } finally {
            synchronized (this) {
                if (selectionKey.isValid()) {
                    logger.debug(debugName + " resuming selection with " + interestToString(cachedInterestOps));
                    dispatcher.setInterestOps(selectionKey, cachedInterestOps);
                }
                opsCached = false;
            }
        }
        logger.debug(debugName + " done");
    }


    public synchronized void cacheOps() throws CancelledKeyException {
        cachedInterestOps = selectionKey.interestOps();
        cachedReadyOps = selectionKey.readyOps();
        logger.debug(debugName + " starting with " + interestToString(cachedInterestOps));
        selectionKey.interestOps(0);
        opsCached = true;
    }


    public synchronized void removeInterestOps(int interestOps) {
        // check, if interestOps are there at all
        if ((cachedInterestOps & interestOps) == 0) {
            // none of interestOps are set, so nothing can be removed!?
            logger.debug(debugName + ": " + interestToString(interestOps) + " not set");
            return;
        }
        // update cache
        cachedInterestOps &= ~interestOps;
        logger.debug(debugName + ": cachedInterestOps set to " + interestToString(cachedInterestOps));
        if (!opsCached) {
            dispatcher.removeInterestOps(selectionKey, interestOps);
        }
    }

    public synchronized void addInterestOps(int interestOps) {
        if ((cachedInterestOps & interestOps) == interestOps) {
            logger.debug(debugName + ": " + interestToString(interestOps) + " was already there");
            return;
        }
        // update cache
        cachedInterestOps |= interestOps;
        logger.debug(debugName + ": cachedInterestOps set to " + interestToString(cachedInterestOps));
        if (!opsCached) {
            // forward change to "real" interestOps if not cached
            dispatcher.setInterestOps(selectionKey, cachedInterestOps);
        }
    }

    public void closeChannel() throws IOException {
        cachedReadyOps = 0;
        dispatcher.closeChannel(selectionKey);
    }

    public synchronized Channel getChannel() {
        return selectionKey.channel();
    }

    public static String interestToString(int interest) {
        StringBuilder stringBuilder = new StringBuilder();
        if ((interest & SelectionKey.OP_ACCEPT) != 0) {
            stringBuilder.append("OP_ACCEPT ");
        }
        if ((interest & SelectionKey.OP_CONNECT) != 0) {
            stringBuilder.append("OP_CONNECT ");
        }
        if ((interest & SelectionKey.OP_READ) != 0) {
            stringBuilder.append("OP_READ ");
        }
        if ((interest & SelectionKey.OP_WRITE) != 0) {
            stringBuilder.append("OP_WRITE ");
        }
        if (stringBuilder.length() == 0) {
            stringBuilder.append("NO INTEREST");
        }
        return stringBuilder.toString();
    }
}
