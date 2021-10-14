package cn.net.scp.nio;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BufferListenerHandler {

    static final Logger logger = LogManager.getLogger();

    private final Object source;
    private List<BufferListener> listenerList;
    private int bufferFillLevel;

    public BufferListenerHandler(Object source) {
        this.source = source;
    }

    public synchronized void addBufferListener(BufferListener listener) {
        if (listenerList == null) {
            listenerList = new ArrayList<>();
        }
        listenerList.add(listener);
    }

    public synchronized void removeBufferListener(BufferListener listener) {
        if (listenerList != null && !listenerList.isEmpty()) {
            listenerList.remove(listener);
        }
    }

    public synchronized void updateLevel(int newLevel) {
        if (logger.isDebugEnabled()) {
            logger.debug("bufferFillLevel = " + bufferFillLevel + ", newLevel = " + newLevel);
        }
        if (bufferFillLevel != newLevel) {
            bufferFillLevel = newLevel;
            if (logger.isDebugEnabled()) {
                logger.debug("bufferFillLevel = " + bufferFillLevel);
            }
            if (listenerList == null || listenerList.isEmpty()) {
                logger.info("bufferListenerList empty");
            } else {
                for (BufferListener bufferListener : listenerList) {
                    bufferListener.bufferChanged(source, newLevel);
                }
            }
        }
    }
}
