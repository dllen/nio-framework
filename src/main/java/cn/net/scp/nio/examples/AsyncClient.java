package cn.net.scp.nio.examples;

import cn.net.scp.nio.AbstractChannelHandler;
import cn.net.scp.nio.Dispatcher;
import cn.net.scp.nio.HandlerAdapter;
import cn.net.scp.nio.transform.AbstractForwarder;
import cn.net.scp.nio.transform.ByteBufferToStringConvertor;
import cn.net.scp.nio.transform.StringToByteBufferConvertor;
import cn.net.scp.nio.utils.Tools;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AsyncClient extends AbstractChannelHandler {

    static final Logger logger = LogManager.getLogger();
    private final Lock lock = new ReentrantLock();
    private final Condition inputArrived = lock.newCondition();

    private Socket socket;
    private String receivedMessage;

    public AsyncClient(String host, int port) {
        //input chain
        ByteBufferToStringConvertor byteBufferToStringConvertor = new ByteBufferToStringConvertor();
        reader.setNextForwarder(byteBufferToStringConvertor);
        EchoTransformer echoTransformer = new EchoTransformer();
        byteBufferToStringConvertor.setNextForwarder(echoTransformer);

        //output chain
        StringToByteBufferConvertor stringToByteBufferConvertor = new StringToByteBufferConvertor();
        stringToByteBufferConvertor.setNextForwarder(writer);

        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
            SocketChannel socketChannel = SocketChannel.open(inetSocketAddress);
            socketChannel.configureBlocking(false);
            socket = socketChannel.socket();

            //
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.start();
            dispatcher.registerChannel(socketChannel, this);

            lock.lock();
            try {
                inputArrived.await();
            } catch (InterruptedException e) {
                Tools.handleStackTrace(logger, e);
            } finally {
                lock.unlock();
            }

            System.out.println("done...");
        } catch (Exception ex) {
            Tools.handleStackTrace(logger, ex);
        }
    }

    @Override
    public void channelRegistered(HandlerAdapter handlerAdapter) {
        logger.debug("channel registered...");
        super.channelRegistered(handlerAdapter);
        try {
            socket.shutdownOutput();
        } catch (IOException e) {
            Tools.handleStackTrace(logger, e);
        }
    }

    @Override
    public void inputClosed() {
        logger.error("AsyncClient closed...");
        System.exit(1);
    }

    @Override
    public void channelException(Exception exception) {
        logger.error("Connection error ", exception);
        System.exit(1);
    }

    public String getReceivedMessage() {
        return receivedMessage;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: AsyncClient <server host> <server port>");
            System.exit(1);
        }
        new AsyncClient(args[0], Integer.parseInt(args[1]));
    }

    private class EchoTransformer extends AbstractForwarder<String, Void> {

        @Override
        public void forward(String input) throws IOException {
            receivedMessage = input;
            System.out.println("received : " + receivedMessage);
            try {
                handlerAdapter.closeChannel();
            } catch (IOException e) {
                Tools.handleStackTrace(logger, e);
            }
            lock.lock();
            try {
                inputArrived.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
