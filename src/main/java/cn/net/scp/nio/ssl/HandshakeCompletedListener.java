package cn.net.scp.nio.ssl;


import javax.net.ssl.SSLSession;

/**
 * a customized version of HandshakeCompletedListener that works without a SSLSocket
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public interface HandshakeCompletedListener {

    /**
     * This method is invoked on registered objects when a SSL handshake is completed.
     * @param session the SSLSession this event is associated with
     */
    void handshakeCompleted(SSLSession session);
}
