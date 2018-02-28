package com.hklh8.common.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 容器启动工具类.
 */
public class ContainerHelper {
    private static Logger logger = LoggerFactory.getLogger(ContainerHelper.class);

    private static volatile boolean running = true;
    private static List<Container> cachedContainers;

    public static void start(List<Container> containers) {
        cachedContainers = containers;
        // 启动所有容器
        startContainers();

        //jvm中增加一个关闭的钩子，当jvm关闭的时候，会执行系统中已经设置的所有通过方法addShutdownHook添加的钩子，
        // 当系统执行完这些钩子后，jvm才会关闭。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (ContainerHelper.class) {
                // 停止所有容器.
                stopContainers();
                running = false;
                ContainerHelper.class.notify();
            }
        }));

        synchronized (ContainerHelper.class) {
            while (running) {
                try {
                    ContainerHelper.class.wait();
                } catch (Throwable e) {
                }
            }
        }
    }

    private static void startContainers() {
        for (Container container : cachedContainers) {
            logger.info("starting container [{}]", container.getClass().getName());
            container.start();
            logger.info("container [{}] started", container.getClass().getName());
        }
    }

    private static void stopContainers() {
        for (Container container : cachedContainers) {
            logger.info("stopping container [{}]", container.getClass().getName());
            try {
                container.stop();
                logger.info("container [{}] stopped", container.getClass().getName());
            } catch (Exception ex) {
                logger.warn("container stopped with error", ex);
            }
        }
    }
}
