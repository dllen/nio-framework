package cn.net.scp.nio.transform;

import java.io.IOException;

public abstract class AbstractForwarder<I, O> {

    protected AbstractForwarder<O, ?> nextForwarder;

    public abstract void forward(I input) throws IOException;

    public synchronized void setNextForwarder(AbstractForwarder<O, ?> nextForwarder) {
        this.nextForwarder = nextForwarder;
    }
}
