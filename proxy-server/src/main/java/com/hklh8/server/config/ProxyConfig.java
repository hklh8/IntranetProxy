package com.hklh8.server.config;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * server config
 */
public class ProxyConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置文件为config.json
     */
    public static final String CONFIG_FILE;

    private static Logger logger = LoggerFactory.getLogger(ProxyConfig.class);

    static {
        // 代理配置信息存放在用户根目录下
        String dataPath = System.getProperty("user.home") + "/" + ".intranetProxy/";
        File file = new File(dataPath);
        if (!file.isDirectory()) {
            file.mkdir();
        }
        CONFIG_FILE = dataPath + "/config.json";
    }

    /**
     * 代理客户端，支持多个客户端
     */
    private List<Client> clients;

    /**
     * 更新配置后保证在其他线程即时生效
     */
    private static ProxyConfig instance = new ProxyConfig();
    ;

    /**
     * 代理服务器为各个代理客户端（key）开启对应的端口列表（value）
     */
    private volatile Map<String, List<Integer>> clientInetPortMapping = new HashMap<>();

    /**
     * 代理服务器上的每个对外端口（key）对应的代理客户端背后的真实服务器信息（value）
     */
    private volatile Map<Integer, String> inetPortLanInfoMapping = new HashMap<>();

    /**
     * 配置变化监听器
     */
    private List<ConfigChangedListener> configChangedListeners = new ArrayList<>();

    private ProxyConfig() {
        update(null);
    }

    public List<Client> getClients() {
        return clients;
    }

    /**
     * 解析配置文件
     */
    public void update(String proxyMappingConfigJson) {

        File file = new File(CONFIG_FILE);
        try {
            if (proxyMappingConfigJson == null && file.exists()) {
                InputStream in = new FileInputStream(file);
                byte[] buf = new byte[1024];
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int readIndex;
                while ((readIndex = in.read(buf)) != -1) {
                    out.write(buf, 0, readIndex);
                }

                in.close();
                proxyMappingConfigJson = new String(out.toByteArray(), Charset.forName("UTF-8"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Client> clients = JSON.parseArray(proxyMappingConfigJson, Client.class);

        if (clients == null) {
            clients = new ArrayList<>();
        }

        Map<String, List<Integer>> clientInetPortMapping = new HashMap<>();
        Map<Integer, String> inetPortLanInfoMapping = new HashMap<>();

        // 构造端口映射关系
        for (Client client : clients) {
            String clientKey = client.getClientKey();
            if (clientInetPortMapping.containsKey(clientKey)) {
                throw new IllegalArgumentException("密钥同时作为客户端标识，不能重复： " + clientKey);
            }
            List<ClientProxyMapping> mappings = client.getProxyMappings();
            List<Integer> ports = new ArrayList<>();
            clientInetPortMapping.put(clientKey, ports);
            for (ClientProxyMapping mapping : mappings) {
                Integer port = mapping.getInetPort();
                ports.add(port);
                if (inetPortLanInfoMapping.containsKey(port)) {
                    throw new IllegalArgumentException("一个公网端口只能映射一个后端信息，不能重复: " + port);
                }

                inetPortLanInfoMapping.put(port, mapping.getLan());
            }
        }

        // 替换之前的配置关系
        this.clientInetPortMapping = clientInetPortMapping;
        this.inetPortLanInfoMapping = inetPortLanInfoMapping;
        this.clients = clients;

        if (proxyMappingConfigJson != null) {
            try {
                FileOutputStream out = new FileOutputStream(file);
                out.write(proxyMappingConfigJson.getBytes(Charset.forName("UTF-8")));
                out.flush();
                out.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        notifyconfigChangedListeners();
    }

    /**
     * 配置更新通知
     */
    private void notifyconfigChangedListeners() {
        List<ConfigChangedListener> changedListeners = new ArrayList<>(configChangedListeners);
        for (ConfigChangedListener changedListener : changedListeners) {
            changedListener.onChanged();
        }
    }

    /**
     * 添加配置变化监听器
     *
     * @param configChangedListener
     */
    public void addConfigChangedListener(ConfigChangedListener configChangedListener) {
        configChangedListeners.add(configChangedListener);
    }

    /**
     * 移除配置变化监听器
     *
     * @param configChangedListener
     */
    public void removeConfigChangedListener(ConfigChangedListener configChangedListener) {
        configChangedListeners.remove(configChangedListener);
    }

    /**
     * 获取代理客户端对应的代理服务器端口
     *
     * @param clientKey
     * @return
     */
    public List<Integer> getClientInetPorts(String clientKey) {
        return clientInetPortMapping.get(clientKey);
    }

    /**
     * 获取所有的clientKey
     *
     * @return
     */
    public Set<String> getClientKeySet() {
        return clientInetPortMapping.keySet();
    }

    /**
     * 根据代理服务器端口获取后端服务器代理信息
     *
     * @param port
     * @return
     */
    public String getLanInfo(Integer port) {
        return inetPortLanInfoMapping.get(port);
    }

    /**
     * 返回需要绑定在代理服务器的端口（用于用户请求）
     *
     * @return
     */
    public List<Integer> getUserPorts() {
        List<Integer> ports = new ArrayList<>();
        Iterator<Integer> ite = inetPortLanInfoMapping.keySet().iterator();
        while (ite.hasNext()) {
            ports.add(ite.next());
        }

        return ports;
    }

    public static ProxyConfig getInstance() {
        return instance;
    }

    /**
     * 代理客户端
     */
    public static class Client implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 客户端备注名称
         */
        private String name;

        /**
         * 代理客户端唯一标识key
         */
        private String clientKey;

        /**
         * 代理客户端与其后面的真实服务器映射关系
         */
        private List<ClientProxyMapping> proxyMappings;

        private int status;

        public String getClientKey() {
            return clientKey;
        }

        public void setClientKey(String clientKey) {
            this.clientKey = clientKey;
        }

        public List<ClientProxyMapping> getProxyMappings() {
            return proxyMappings;
        }

        public void setProxyMappings(List<ClientProxyMapping> proxyMappings) {
            this.proxyMappings = proxyMappings;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

    }

    /**
     * 代理客户端与其后面真实服务器映射关系
     */
    public static class ClientProxyMapping {

        /**
         * 代理服务器端口
         */
        private Integer inetPort;

        /**
         * 需要代理的网络信息（代理客户端能够访问），格式 192.168.1.99:80 (必须带端口)
         */
        private String lan;

        /**
         * 备注名称
         */
        private String name;

        public Integer getInetPort() {
            return inetPort;
        }

        public void setInetPort(Integer inetPort) {
            this.inetPort = inetPort;
        }

        public String getLan() {
            return lan;
        }

        public void setLan(String lan) {
            this.lan = lan;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    /**
     * 配置更新回调
     */
    public static interface ConfigChangedListener {
        void onChanged();
    }
}
