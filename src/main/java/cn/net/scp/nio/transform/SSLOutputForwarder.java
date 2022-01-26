package cn.net.scp.nio.transform;

import cn.net.scp.nio.BufferListener;
import cn.net.scp.nio.BufferListenerHandler;
import cn.net.scp.nio.ssl.HandshakeNotifier;
import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SSLOutputForwarder extends AbstractForwarder<ByteBuffer[], ByteBuffer> {

    static final Logger logger = LogManager.getLogger();

    private final AtomicLong plainTextCounter = new AtomicLong();

    private final SSLEngine sslEngine;
    // GuardedBy("this")
    private final ByteBuffer[] plainTextArray = new ByteBuffer[1];
    private ByteBuffer plainText, cipherText;
    private boolean switchToInput;
    // buffer fill state monitoring
    private final BufferListenerHandler bufferListenerHandler;
    /**
     * The HandshakeNotifier is an utility class shared by an
     * SSLInputForwarder and its SSLOutputForwarder. It forwards "handshake
     * completed" events to a list of registered listeners.
     */
    private HandshakeNotifier handshakeNotifier;
    private SSLInputForwarder sslInputForwarder;

    /**
     * Creates a new instance of SSLOutputForwarder
     * @param sslEngine the given {@link javax.net.ssl.SSLEngine SSLEngine} used
     * for encryption of outbound data
     * @param plainTextBufferSize the initial size of the plaintext buffer
     */
    public SSLOutputForwarder(SSLEngine sslEngine, int plainTextBufferSize) {
        this.sslEngine = sslEngine;
        /**
         * The plainText ByteBuffer holds the unencrypted plaintext data. It is
         * normally in "drain" mode, i.e. user data is in range including
         * [position() ... limit() - 1].
         */
        plainText = ByteBuffer.allocate(plainTextBufferSize);
        plainText.flip();
        /**
         * The cipherText ByteBuffer holds the encrypted ciphertext date.
         */
        int cipherSize = sslEngine.getSession().getPacketBufferSize() + 1000;
        cipherText = ByteBuffer.allocate(cipherSize);
        cipherText.flip();

        bufferListenerHandler = new BufferListenerHandler(this);
    }

    /**
     * sets the corresponding SSLInputForwarder
     * @param sslInputForwarder the corresponding SSLInputForwarder
     */
    public void setSSLInputForwarder(SSLInputForwarder sslInputForwarder) {
        this.sslInputForwarder = sslInputForwarder;
    }

    /**
     * registers BufferListener as event receiver.
     * @param listener the listener to be registered
     */
    public synchronized void addBufferSizeListener(BufferListener listener) {
        bufferListenerHandler.addBufferListener(listener);
    }

    /**
     * removes a BufferListener as event receiver.
     * @param listener the listener to be removed
     */
    public synchronized void removeBufferSizeListener(BufferListener listener) {
        bufferListenerHandler.removeBufferListener(listener);
    }

    /**
     * sets the {@link cn.net.scp.nio.ssl.HandshakeNotifier
     * HandshakeNotifier}
     * @param handshakeNotifier the {@link
     * cn.net.scp.nio.ssl.HandshakeNotifier HandshakeNotifier}
     */
    public void setHandshakeNotifier(HandshakeNotifier handshakeNotifier) {
        this.handshakeNotifier = handshakeNotifier;
    }

    @Override
    public synchronized void forward(ByteBuffer[] inputs) throws IOException {
        // accounting
        for (ByteBuffer input : inputs) {
            plainTextCounter.addAndGet(input.remaining());
        }

        if (plainText.hasRemaining()) {
            // There is already some pending data.
            // We must append the input buffers to plainText.
            logger.trace("plainText: " + plainText);
            for (ByteBuffer input : inputs) {
                logger.trace("appending input (" + input + ") to plainText (" + plainText + ")");
                plainText = Tools.append(false, plainText, input);
            }

        } else {
            // encrypt and forward input
            cipherText.compact();
            SSLEngineResult sslEngineResult = null;
            do {
                if (logger.isTraceEnabled()) {
                    StringBuilder stringBuilder = new StringBuilder();
                    for (int i = 0; i < inputs.length; i++) {
                        stringBuilder.append("\tinput ").append(i).append(": ").append(inputs[i]);
                        if (i < (inputs.length - 1)) {
                            stringBuilder.append('\n');
                        }
                    }
                    logger.trace("input:\n" + stringBuilder);
                }
                sslEngineResult = sslEngine.wrap(inputs, cipherText);
                if (logger.isTraceEnabled()) {
                    logger.trace("bytesProduced = " + sslEngineResult.bytesProduced());
                }
            } while (continueWrapping(sslEngineResult, inputs));
            cipherText.flip();

            // if encryption was incomplete we must append the remaining data
            for (ByteBuffer input : inputs) {
                if (input.hasRemaining()) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("appending " + input + " to plainText");
                    }
                    plainText = Tools.append(false, plainText, input);
                }
            }

            // forward
            if (cipherText.hasRemaining()) {
                if (nextForwarder == null) {
                    logger.error("no nextForwarder => data lost!");
                } else {
                    nextForwarder.forward(cipherText);
                }
            }
        }

        cleanUp();
    }

    /**
     * returns the number of Bytes in the plaintext buffer
     * @return the number of Bytes in the plaintext buffer
     */
    public synchronized int getRemainingPlaintext() {
        return plainText.remaining();
    }

    /**
     * returns true, if the plaintext buffer has remaining data,
     * false othterwise
     * @return true, if the plaintext buffer has remaining data,
     * false othterwise
     */
    public synchronized boolean hasRemainingPlaintext() {
        return plainText.hasRemaining();
    }

    /**
     * returns how many bytes of plain text have been forwarded
     * @return how many bytes of plain text have been forwarded
     */
    public long getPlainTextCounter() {
        return plainTextCounter.get();
    }

    /**
     * resets the plain text counter back to zero and returns the amount of
     * forwarded plain text in byte
     * @return the amount of forwarded plain text in byte
     */
    public long getAndResetPlainTextCounter() {
        return plainTextCounter.getAndSet(0);
    }

    synchronized void drain() throws IOException {
        /**
         * !!! We must not test here if the plaintext buffer contains data
         * before encryption. It may be empty when handshaking!!!
         */
        cipherText.compact();
        SSLEngineResult sslEngineResult = null;
        plainTextArray[0] = plainText;
        do {
            if (logger.isTraceEnabled()) {
                logger.trace("plainText: " + plainText);
            }
            sslEngineResult = sslEngine.wrap(plainText, cipherText);
            if (logger.isTraceEnabled()) {
                logger.trace("bytesProduced = " + sslEngineResult.bytesProduced());
            }
        } while (continueWrapping(sslEngineResult, plainTextArray));
        cipherText.flip();

        if (logger.isTraceEnabled()) {
            logger.trace("cipherText after encryption: " + cipherText);
        }

        // forward data, if necessary and possible
        if (cipherText.hasRemaining() && (nextForwarder != null)) {
            nextForwarder.forward(cipherText);
        }

        cleanUp();
    }

    /**
     * checks, if we can continue with wrap() operations at the SSLEngine
     * @return <CODE>true</CODE>, if we may continue wrapping,
     * <CODE>false</CODE> otherwise
     * @param sslEngineResult the current SSLEngine result of the last wrap()
     * operation
     * @param buffers the message to be encrypted (wrapped)
     */
    @SuppressWarnings("fallthrough")
    private boolean continueWrapping(SSLEngineResult sslEngineResult, ByteBuffer[] buffers) {

        SSLEngineResult.Status status = sslEngineResult.getStatus();
        if (logger.isTraceEnabled()) {
            logger.trace("status: " + status);
        }
        switch (status) {
            case BUFFER_OVERFLOW:
                // just enlarge buffer and retry wrapping
                int newCapacity = cipherText.capacity() + 1000;
                cipherText = Tools.enlargeBuffer(cipherText, newCapacity);
                if (logger.isTraceEnabled()) {
                    logger.trace("enlarged cipherText: " + cipherText);
                }
                return true;

            case OK:
                // wrap() was successfully completed
                SSLEngineResult.HandshakeStatus handshakeStatus = sslEngineResult.getHandshakeStatus();
                for (boolean checkHandshake = true; checkHandshake; ) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("handshakeStatus: " + handshakeStatus);
                    }
                    switch (handshakeStatus) {
                        case NEED_WRAP:
                            return true;
                        case NEED_UNWRAP:
                            switchToInput = true;
                            return false;
                        case NEED_TASK:
                            for (Runnable runnable = sslEngine.getDelegatedTask(); runnable != null; ) {
                                runnable.run();
                                runnable = sslEngine.getDelegatedTask();
                            }
                            handshakeStatus = sslEngine.getHandshakeStatus();
                            break;
                        case FINISHED:
                            if (handshakeNotifier != null) {
                                handshakeNotifier.fireHandshakeCompleted(null, sslEngine.getSession());
                            }
                            // there may already be some cipher text waiting in
                            // the sslInputForwarder...
                            switchToInput = true;
                        case NOT_HANDSHAKING:
                        default:
                            checkHandshake = false;
                    }
                }

                for (ByteBuffer buffer : buffers) {
                    if (buffer.hasRemaining()) {
                        return true;
                    }
                }
                return false;

            case CLOSED:
            default:
                // fall through here
                // INFO: BUFFER_UNDERFLOW can not happen here...
                return false;
        }
    }

    private void cleanUp() throws IOException {

        // detect plain text fill level changes
        int remaining = plainText.remaining();
        bufferListenerHandler.updateLevel(remaining);

        // do we have to continue the handshake at the input forwarder?
        if (switchToInput) {
            switchToInput = false;
            if (sslInputForwarder == null) {
                throw new IOException("sslInputForwarder is not set! " + "Use setSSLInputForwarder(...) directly after " + "creating an SSLOutputForwarder.");
            }
            logger.trace("switching to sslInputForwarder");
            sslInputForwarder.continueHandshake();
        }
    }


}
