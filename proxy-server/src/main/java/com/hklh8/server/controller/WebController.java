package com.hklh8.server.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hklh8.server.ProxyChannelManager;
import com.hklh8.server.config.ProxyConfig;
import com.hklh8.server.dto.ResponseInfo;
import com.hklh8.server.metrics.MetricsCollector;
import com.hklh8.server.utils.PropertiesValue;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.UUID;

@Controller
public class WebController {
    private static Logger logger = LoggerFactory.getLogger(WebController.class);

    /**
     * 管理员不能同时在多个地方登录
     */
    private static String token;

    /**
     * 获取配置详细信息
     */
    @ResponseBody
    @RequestMapping("/config/detail")
    public Object getConfig() {
        List<ProxyConfig.Client> clients = ProxyConfig.getInstance().getClients();
        for (ProxyConfig.Client client : clients) {
            Channel channel = ProxyChannelManager.getCmdChannel(client.getClientKey());
            if (channel != null) {
                client.setStatus(1);//上线
            } else {
                client.setStatus(0);//下线
            }
        }
        return ResponseInfo.build(ProxyConfig.getInstance().getClients());
    }

    /**
     * 更新配置
     */
    @ResponseBody
    @RequestMapping("/config/update")
    public Object updateConfig(@RequestBody String config) {
        List<ProxyConfig.Client> clients = JSON.parseArray(config, ProxyConfig.Client.class);

        if (clients == null) {
            return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error json config");
        }

        try {
            ProxyConfig.getInstance().update(config);
        } catch (Exception ex) {
            logger.error("config update error", ex);
            return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, ex.getMessage());
        }

        return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
    }

    @ResponseBody
    @RequestMapping("/login")
    public Object login(@RequestBody String config) {
        JSONObject jsonObject = JSON.parseObject(config);
        if (jsonObject == null) {
            return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error login info");
        }

        String username = jsonObject.getString("username");
        String password = jsonObject.getString("password");

        if (username == null || password == null) {
            return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error username or password");
        }

        if (username.equals(PropertiesValue.getStringValue("config.admin.username", "admin")) &&
                password.equals(PropertiesValue.getStringValue("config.admin.password", "admin"))) {
            token = UUID.randomUUID().toString().replace("-", "");
            return ResponseInfo.build(token);
        }
        return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error username or password");
    }

    @ResponseBody
    @RequestMapping("/logout")
    public Object logout() {
        token = null;
        return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
    }

    /**
     * 获取数据统计
     */
    @ResponseBody
    @RequestMapping("/metrics/get")
    public Object test4() {
        return ResponseInfo.build(MetricsCollector.getAllMetrics());
    }

    @ResponseBody
    @RequestMapping("/metrics/getandreset")
    public Object test5() {
        return ResponseInfo.build(MetricsCollector.getAndResetAllMetrics());
    }
}
