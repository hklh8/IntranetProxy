package com.hklh8.server;

import com.hklh8.common.container.ContainerHelper;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Created by GouBo on 2018/2/9.
 */
@Component
public class ProxyServerStarter implements ApplicationListener<ContextRefreshedEvent> {
    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        new Thread(() -> ContainerHelper.start(Collections.singletonList(new ProxyServerContainer()))).start();
    }
}