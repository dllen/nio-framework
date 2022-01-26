package cn.net.scp.nio.transform;

import java.io.IOException;

/**
 * An interface for transformers that do traffic shaping (i.e. can be
 * coordinated by the TrafficShaperCoordinator).
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public interface TrafficShaper {

    /**
     * sends a package with a given maximum package size
     * @param maxPackageSize the given maximum package size
     * @throws IOException if there is an I/O exception while sending the
     * package
     */
    void sendPackage(long maxPackageSize) throws IOException;

    /**
     * Gets called by the TrafficShaperCoordinator, when traffic shaping is
     * switched on or off. This way TrafficShaper may reorganize some internal
     * structures when beeing switched on or off.
     * @param active if <code>true</code>, traffic shaping is turned on,
     * otherwise it is switched off
     */
    void setActive(boolean active);
}
