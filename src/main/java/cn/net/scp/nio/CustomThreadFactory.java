package cn.net.scp.nio;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {

    static final AtomicInteger poolNumber = new AtomicInteger(1);

    final ThreadGroup threadGroup;
    final AtomicInteger threadNumber = new AtomicInteger(1);
    final String namePrefix;

    public CustomThreadFactory() {
        SecurityManager securityManager = System.getSecurityManager();
        threadGroup = securityManager == null ? Thread.currentThread().getThreadGroup() : securityManager.getThreadGroup();
        namePrefix = "NIO-Thread-Pool-" + poolNumber.getAndIncrement() + "-Thread-";

    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(threadGroup, r, namePrefix + threadNumber.getAndIncrement());
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }
        thread.setDaemon(true);
        return thread;
    }
}
