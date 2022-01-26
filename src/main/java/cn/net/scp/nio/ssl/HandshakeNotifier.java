package cn.net.scp.nio.ssl;

import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * notifies listeners about completed handshakes
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class HandshakeNotifier {

    /**
     * Utility field holding list of HandshakeCompletedListeners.
     */
    private List<HandshakeCompletedListener> listenerList;

    /**
     * Registers HandshakeCompletedListener to receive events.
     * @param listener The listener to register.
     */
    public synchronized void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        if (listenerList == null) {
            listenerList = new ArrayList<>();
        }
        listenerList.add(listener);
    }

    /**
     * Removes HandshakeCompletedListener from the list of listeners.
     * @param listener The listener to remove.
     */
    public synchronized void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        if (listenerList != null) {
            listenerList.remove(listener);
        }
    }

    /**
     * Notifies all registered listeners about the event.
     * @param sslSocket
     * @param sslSession the SSLSession where the handshake completed
     */
    public synchronized void fireHandshakeCompleted(SSLSocket sslSocket, SSLSession sslSession) {
        if (listenerList != null) {
            for (int i = 0, size = listenerList.size(); i < size; i++) {
                HandshakeCompletedListener listener = listenerList.get(i);
                listener.handshakeCompleted(new HandshakeCompletedEvent(sslSocket, sslSession));
            }
        }
    }
}
