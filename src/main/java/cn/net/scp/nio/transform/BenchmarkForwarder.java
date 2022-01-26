package cn.net.scp.nio.transform;

import cn.net.scp.nio.BufferListener;
import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A forwarder that generates data as fast as possible.
 * It stops when incomplete write operations occur at the ChannelWriter and
 * resumes when the buffer at the ChannelWriter is empty again.
 *
 * Optionally, the BenchmarkForwarder can monitor an SSLOutputForwarder and
 * stop when there is remaining plaintext.
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class BenchmarkForwarder extends AbstractForwarder<Void, ByteBuffer> implements BufferListener {

    static final Logger logger = LogManager.getLogger();

    private ChannelWriter channelWriter;
    private SSLOutputForwarder sslOutputForwarder;
    private int channelLevel, sslOutputLevel;
    private boolean noBufferedData = true;
    private boolean keepRunning = true;
    private final ByteBuffer buffer;

    /**
     * Creates a new BenchmarkForwarder
     * @param bufferSize the size of the buffer that will be used for the
     * benchmark
     * @param direct if true, the buffer will be allocated directly, if false,
     * the buffer will be allocated normally
     */
    public BenchmarkForwarder(int bufferSize, boolean direct) {
        if (direct) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
        } else {
            buffer = ByteBuffer.allocate(bufferSize);
        }
    }

    @Override
    public synchronized void forward(Void input) throws IOException {
        // generate data as long as possible
        while (keepRunning && noBufferedData && (nextForwarder != null)) {
            buffer.clear();
            if (logger.isTraceEnabled()) {
                logger.trace("calling nextForwarder.forward(" + buffer + ')');
            }
            nextForwarder.forward(buffer);
        }
    }

    /**
     * stops the forwarder, i.e. no more data is generated and forwarded
     * !!! DO NOT SYNCHRONIZE THIS CALL, forward() HOLDS THE LOCK MOST OF THE
     * TIME!!!
     */
    public void stop() {
        keepRunning = false;
    }

    /**
     * resumes the forwarder, i.e. new data is generated and forwarded
     */
    public void resume() {
        keepRunning = true;
    }

    /**
     * sets the channelWriter to monitor for incomplete write operations
     * @param channelWriter the channelWriter to monitor for incomplete write
     * operations
     */
    public synchronized void setChannelWriter(ChannelWriter channelWriter) {
        if (this.channelWriter != null) {
            // stop monitoring the old ChannelWriter
            this.channelWriter.removeBufferSizeListener(this);
        }
        this.channelWriter = channelWriter;
        channelWriter.addBufferSizeListener(this);
    }

    /**
     * sets the SSLOutputForwarder to monitor for incomplete encryptions
     * @param sslOutputForwarder the SSLOutputForwarder to monitor for
     * incomplete encryptions
     */
    public synchronized void setSSLOutputForwarder(SSLOutputForwarder sslOutputForwarder) {
        if (this.sslOutputForwarder != null) {
            // stop monitoring the old SSLOutputForwarder
            this.sslOutputForwarder.removeBufferSizeListener(this);
        }
        this.sslOutputForwarder = sslOutputForwarder;
        sslOutputForwarder.addBufferSizeListener(this);
    }

    @Override
    public synchronized void bufferChanged(Object source, int newLevel) {
        if (source == channelWriter) {
            channelLevel = newLevel;
        } else if (source == sslOutputForwarder) {
            sslOutputLevel = newLevel;
        }
        if ((channelLevel == 0) && (sslOutputLevel == 0)) {
            try {
                logger.trace("can continue writing");
                noBufferedData = true;
                forward(null);
            } catch (IOException ex) {
                Tools.handleStackTrace(logger, ex);
            }
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("must stop writing:\n\t" + "channelLevel = " + channelLevel + " byte, " + "sslOutputLevel = " + sslOutputLevel + " byte");
            }
            noBufferedData = false;
        }
    }
}
