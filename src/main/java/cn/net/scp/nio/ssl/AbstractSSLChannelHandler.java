package cn.net.scp.nio.ssl;

import cn.net.scp.nio.ChannelHandler;
import cn.net.scp.nio.HandlerAdapter;
import cn.net.scp.nio.transform.ByteBufferToArrayForwarder;
import cn.net.scp.nio.transform.ChannelReader;
import cn.net.scp.nio.transform.ChannelWriter;
import cn.net.scp.nio.transform.SSLInputForwarder;
import cn.net.scp.nio.transform.SSLOutputForwarder;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * A ChannelHandler for SSL connections that takes care for the details when
 * using an SSLInputForwarder and SSLOutputForwarder, like SSLEngine
 * preparation and setting up all required cross references.
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public abstract class AbstractSSLChannelHandler implements ChannelHandler {

    /**
     * the ChannelReader of this ChannelHandler
     */
    protected final ChannelReader channelReader;
    /**
     * the ChannelWriter of this ChannelHandler
     */
    protected final ChannelWriter channelWriter;
    /**
     * the SSLEngine used for the encryption and decryption of SSL traffic
     */
    protected final SSLEngine sslEngine;
    /**
     * the forwarder for incoming encrypted data (produces plaintext)
     */
    protected final SSLInputForwarder sslInputForwarder;
    /**
     * the forwarder for outgoing plaintext data (produces ciphertext)
     */
    protected final SSLOutputForwarder sslOutputForwarder;
    /**
     * a forwarder for outgoing plaintext ByteBuffers
     */
    protected final ByteBufferToArrayForwarder byteBufferToArrayForwarder;
    /**
     * the notifier that fires when the SSL handshake finished
     */
    protected final HandshakeNotifier handshakeNotifier;
    /**
     * the SocketChannel used for this SSL channel
     */
    protected final SocketChannel socketChannel;
    /**
     * the reference to the HandlerAdapter (used for changing interest ops)
     */
    protected HandlerAdapter handlerAdapter;

    /**
     * Creates a new instance of SSLChannelHandler
     * @param sslContext the SSL Context for this ChannelHandler
     * @param socketChannel the SocketChannel for this ChannelHandler
     * @param clientMode <CODE>true</CODE>, if this handler is used at the
     * client side,
     * <CODE>false</CODE> otherwise
     * @param initialReaderBufferSize the initial buffer size for the channel
     * reader
     * @param maxReaderBufferSize the maximum buffer size for the channel reader
     * @param initialPlainTextBufferSize the initial size for the buffer that
     * holds outgoing plaintext
     */
    public AbstractSSLChannelHandler(SocketChannel socketChannel,
        SSLContext sslContext, boolean clientMode,
        int initialReaderBufferSize, int maxReaderBufferSize,
        int initialPlainTextBufferSize) {

        this.socketChannel = socketChannel;

        // prepare a new SSLEngine for the forwarders
        Socket socket = socketChannel.socket();
        String remoteAddress = socket.getInetAddress().getHostAddress();
        int remotePort = socket.getPort();
        sslEngine = sslContext.createSSLEngine(remoteAddress, remotePort);
        sslEngine.setUseClientMode(clientMode);

        handshakeNotifier = new HandshakeNotifier();

        // establish input transformation chain:
        // reader -> sslInput
        sslInputForwarder = new SSLInputForwarder(sslEngine);
        sslInputForwarder.setHandshakeNotifier(handshakeNotifier);
        channelReader = new ChannelReader(
            false, initialReaderBufferSize, maxReaderBufferSize);
        channelReader.setNextForwarder(sslInputForwarder);

        // establish output transformation chain:
        // byteBufferToArray -> sslOutput -> channelWriter
        sslOutputForwarder = new SSLOutputForwarder(
            sslEngine, initialPlainTextBufferSize);
        sslOutputForwarder.setHandshakeNotifier(handshakeNotifier);
        channelWriter = new ChannelWriter(false);
        sslOutputForwarder.setNextForwarder(channelWriter);
        byteBufferToArrayForwarder = new ByteBufferToArrayForwarder();
        byteBufferToArrayForwarder.setNextForwarder(sslOutputForwarder);

        // cross reference both transformers (needed for handshake)
        sslInputForwarder.setSSLOutputForwarder(sslOutputForwarder);
        sslOutputForwarder.setSSLInputForwarder(sslInputForwarder);
    }

    @Override
    public ChannelReader getChannelReader() {
        return channelReader;
    }

    @Override
    public ChannelWriter getChannelWriter() {
        return channelWriter;
    }

    @Override
    public void channelRegistered(HandlerAdapter handlerAdapter) {
        this.handlerAdapter = handlerAdapter;
    }
}
