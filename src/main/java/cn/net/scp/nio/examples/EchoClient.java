package cn.net.scp.nio.examples;

import cn.net.scp.nio.AbstractChannelHandler;
import cn.net.scp.nio.Dispatcher;
import cn.net.scp.nio.transform.AbstractForwarder;
import cn.net.scp.nio.transform.ByteBufferToStringConvertor;
import cn.net.scp.nio.transform.StringToByteBufferConvertor;
import cn.net.scp.nio.utils.Tools;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EchoClient extends AbstractChannelHandler {

    static final Logger logger = LogManager.getLogger();

    private final Lock lock = new ReentrantLock();
    private final Condition inputArrived = lock.newCondition();

    @Override
    public void inputClosed() {
        System.out.println("EchoServer closed the connection");
        System.exit(1);
    }

    @Override
    public void channelException(Exception exception) {
        System.out.println("Connection error " + exception);
        System.exit(1);
    }


    /**
     * Creates a new instance of EchoClient
     *
     * @param host the echo server host name
     * @param port the port of the echo server
     */
    public EchoClient(String host, int port) {

        // setup input chain
        ByteBufferToStringConvertor byteBufferToStringConvertor = new ByteBufferToStringConvertor();
        reader.setNextForwarder(byteBufferToStringConvertor);
        EchoClientForwarder echoTransformer = new EchoClientForwarder();
        byteBufferToStringConvertor.setNextForwarder(echoTransformer);

        // setup output chain
        StringToByteBufferConvertor stringToByteBufferConvertor = new StringToByteBufferConvertor();
        stringToByteBufferConvertor.setNextForwarder(writer);

        try {
            // connect to server
            InetSocketAddress socketAddress = new InetSocketAddress(host, port);
            SocketChannel channel = SocketChannel.open(socketAddress);
            channel.configureBlocking(false);

            // start NIO Framework
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.start();
            dispatcher.registerChannel(channel, this);

            // send all user input to echo server
            System.out.println("EchoClient is running...");
            InputStreamReader streamReader = new InputStreamReader(System.in);
            BufferedReader stdIn = new BufferedReader(streamReader);
            while (true) {
                System.out.println("Your input: ");
                String userInput = stdIn.readLine();
                if (userInput.length() == 0) {
                    continue;
                }
                System.out.println("sending : " + userInput);
                stringToByteBufferConvertor.forward(userInput);
                // wait until we get an echo from the server...
                lock.lock();
                try {
                    inputArrived.await();
                } catch (InterruptedException ex) {
                    Tools.handleStackTrace(logger, ex);
                } finally {
                    lock.unlock();
                }
            }
        } catch (IOException ex) {
            Tools.handleStackTrace(logger, ex);
        }
    }

    /**
     * starts the EchoClient
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: EchoClient <server host> <server port>");
            System.exit(1);
        }
        new EchoClient(args[0], Integer.parseInt(args[1]));
    }

    private class EchoClientForwarder extends AbstractForwarder<String, Void> {

        @Override
        public void forward(String input) throws IOException {
            // print out incoming string
            System.out.println("received : " + input);
            // signal that input has arrived
            lock.lock();
            try {
                inputArrived.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

}
