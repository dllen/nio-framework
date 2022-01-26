package cn.net.scp.nio.transform;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DummyTrafficInputForwarder extends AbstractForwarder<ByteBuffer, ByteBuffer> {

    static final Logger logger = LogManager.getLogger();


    @Override
    public void forward(ByteBuffer input) throws IOException {

    }
}
