package com.hklh8.server;

import com.hklh8.common.container.ContainerHelper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

/**
 * Created by GouBo on 2018/2/4.
 */
@SpringBootApplication
public class StartApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(StartApplication.class, args);

        ContainerHelper.start(Arrays.asList(new ProxyServerContainer()));
    }
}
