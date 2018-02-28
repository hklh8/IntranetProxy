package com.hklh8.client.config;

import com.hklh8.client.ProxyClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 程序入口
 * Created by GouBo on 2018/2/4.
 */
@Component
public class ApplicationEntrance implements CommandLineRunner {

    @Autowired
    ProxyClient proxyClient;

    @Override
    public void run(String... args) {
        proxyClient.start();
        //jvm中增加一个关闭的钩子
        Runtime.getRuntime().addShutdownHook(new Thread(proxyClient::stop));
    }
}
