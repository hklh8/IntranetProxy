package com.hklh8.server;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Created by GouBo on 2018/2/9.
 */
@Component
public class ProxyServerStarter implements ApplicationListener<ContextRefreshedEvent> {
    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        ProxyServerContainer server = new ProxyServerContainer();
        server.start();
        //jvm中增加一个关闭的钩子
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
