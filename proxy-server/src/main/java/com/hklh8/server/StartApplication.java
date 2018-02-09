package com.hklh8.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * Created by GouBo on 2018/2/4.
 */
@ServletComponentScan
@SpringBootApplication
public class StartApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(StartApplication.class, args);
    }
}
