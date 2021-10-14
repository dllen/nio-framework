package cn.net.scp.nio.examples;

import cn.net.scp.nio.AbstractAcceptor;
import cn.net.scp.nio.AbstractChannelHandler;
import cn.net.scp.nio.ChannelHandler;
import cn.net.scp.nio.Dispatcher;
import cn.net.scp.nio.HandlerAdapter;
import cn.net.scp.nio.transform.StringToByteBufferConvertor;
import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AsyncServer extends AbstractAcceptor {

    static final Logger logger = LogManager.getLogger();


    public AsyncServer(Dispatcher dispatcher, SocketAddress socketAddress) throws IOException {
        super(dispatcher, socketAddress);
    }

    @Override
    protected ChannelHandler getHandler(SocketChannel socketChannel) {
        return new AsyncChannelHandler();
    }

    /**
     * starts the AsyncServer
     *
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        if (args.length != 1) {
            System.out.println("Usage: AsyncServer <port>");
            System.exit(1);
        }

        try {
            // start NIO Framework
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.start();

            // start AsyncServer
            int port = Integer.parseInt(args[0]);
            SocketAddress socketAddress = new InetSocketAddress(port);
            AsyncServer asyncServer = new AsyncServer(dispatcher, socketAddress);
            asyncServer.start();
            System.out.println("AsyncServer is running at port " + port + "...");

        } catch (Exception ex) {
            Tools.handleStackTrace(logger, ex);
        }
    }


    /**
     *
     */
    private static class AsyncChannelHandler extends AbstractChannelHandler {

        private final StringToByteBufferConvertor stringToByteBufferConvertor;

        public AsyncChannelHandler() {
            //set up I/O
            stringToByteBufferConvertor = new StringToByteBufferConvertor();
            stringToByteBufferConvertor.setNextForwarder(writer);
        }

        @Override
        public void channelRegistered(HandlerAdapter handlerAdapter) {
            super.channelRegistered(handlerAdapter);
        }

        @Override
        public void inputClosed() {
            logger.debug("AsyncClient closed the connection.");
            try {
                sleep(3000);
                stringToByteBufferConvertor.forward("connection shutdown");
            } catch (Exception e) {
                Tools.handleStackTrace(logger, e);
            }
        }

        @Override
        public void channelException(Exception exception) {
            logger.warn("Exception on channel: ", exception);
            try {
                handlerAdapter.closeChannel();
            } catch (IOException e) {
                Tools.handleStackTrace(logger, e);
            }
        }
    }
}
