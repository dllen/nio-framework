package cn.net.scp.nio.examples;

import cn.net.scp.nio.AbstractAcceptor;
import cn.net.scp.nio.AbstractChannelHandler;
import cn.net.scp.nio.ChannelHandler;
import cn.net.scp.nio.Dispatcher;
import cn.net.scp.nio.transform.AbstractForwarder;
import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EchoServer extends AbstractAcceptor {

    static final Logger logger = LogManager.getLogger();

    /**
     * creates a new EchoServer
     *
     * @param dispatcher    the central NIO dispatcher
     * @param socketAddress the address where to listen to
     * @throws java.io.IOException if an I/O exception occurs
     */
    public EchoServer(Dispatcher dispatcher, SocketAddress socketAddress) throws IOException {
        super(dispatcher, socketAddress);
    }


    @Override
    protected ChannelHandler getHandler(SocketChannel socketChannel) {
        return new EchoChannelHandler();
    }

    /**
     * starts the EchoServer
     *
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        if (args.length != 1) {
            System.out.println("Usage: EchoServer <port>");
            System.exit(1);
        }
        try {
            // start NIO Framework
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.start();
            // start EchoServer
            int port = Integer.parseInt(args[0]);
            SocketAddress socketAddress = new InetSocketAddress(port);
            EchoServer echoServer = new EchoServer(dispatcher, socketAddress);
            echoServer.start();
            System.out.println("EchoServer is running at port " + port + "...");
        } catch (Exception ex) {
            Tools.handleStackTrace(logger, ex);
        }
    }

    private static class EchoChannelHandler extends AbstractChannelHandler {

        public EchoChannelHandler() {
            // set up I/O
            EchoServerForwarder echoServerForwarder = new EchoServerForwarder();
            reader.setNextForwarder(echoServerForwarder);
            echoServerForwarder.setNextForwarder(writer);
        }

        @Override
        public void inputClosed() {
            System.out.println("EchoClient closed the connection");
            try {
                handlerAdapter.closeChannel();
            } catch (IOException ex) {
                Tools.handleStackTrace(logger, ex);
            }
        }

        @Override
        public void channelException(Exception exception) {
            System.out.println("Exception on channel: " + exception);
            try {
                handlerAdapter.closeChannel();
            } catch (IOException ex) {
                Tools.handleStackTrace(logger, ex);
            }
        }
    }

    private static class EchoServerForwarder extends AbstractForwarder<ByteBuffer, ByteBuffer> {

        @Override
        public void forward(ByteBuffer input) throws IOException {
            // echo all data we get...
            System.out.println("echoing " + input.remaining() + " bytes");
            nextForwarder.forward(input);
        }
    }
}
