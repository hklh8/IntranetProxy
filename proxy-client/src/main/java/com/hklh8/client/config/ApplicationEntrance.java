package com.hklh8.client.config;

import com.hklh8.client.ProxyClientContainer;
import com.hklh8.common.container.ContainerHelper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 程序入口
 * Created by GouBo on 2018/2/4.
 */
@Component
public class ApplicationEntrance implements CommandLineRunner {

    @Override
    public void run(String... args) {
        new Thread(() -> ContainerHelper.start(Collections.singletonList(new ProxyClientContainer()))).start();
    }
}
