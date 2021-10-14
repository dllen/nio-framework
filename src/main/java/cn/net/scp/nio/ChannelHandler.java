package cn.net.scp.nio;

import cn.net.scp.nio.transform.ChannelReader;
import cn.net.scp.nio.transform.ChannelWriter;

public interface ChannelHandler {

    void channelRegistered(HandlerAdapter handlerAdapter);

    ChannelReader getChannelReader();

    ChannelWriter getChannelWriter();

    void inputClosed();

    void channelException(Exception exception);

}
