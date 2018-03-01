package com.hklh8.client;

import com.hklh8.client.utils.PropertiesValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class SslContextCreator {

    private static Logger logger = LoggerFactory.getLogger(SslContextCreator.class);

    public static SSLContext createSSLContext() {
        return new SslContextCreator().initSSLContext();
    }

    public SSLContext initSSLContext() {
        logger.info("检查SSL配置属性...");
        final String jksPath = PropertiesValue.getStringValue("ssl.jksPath");
        logger.info("初始化SSL context. KeystorePath = {}.", jksPath);
        if (jksPath == null || jksPath.isEmpty()) {
            // key_store_password or key_manager_password are empty
            logger.warn("keystore路径是null或空。SSL context不会初始化。");
            return null;
        }

        final String keyStorePassword = PropertiesValue.getStringValue("ssl.keyStorePassword");

        try {
            logger.info("加载 keystore. KeystorePath = {}.", jksPath);
            InputStream jksInputStream = jksDatastore(jksPath);
            SSLContext clientSSLContext = SSLContext.getInstance("TLS");
            final KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(jksInputStream, keyStorePassword.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            TrustManager[] trustManagers = tmf.getTrustManagers();

            // init sslContext
            logger.info("初始化 SSL context...");
            clientSSLContext.init(null, trustManagers, null);
            logger.info("SSL context 初始化成功...");

            return clientSSLContext;
        } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException | KeyManagementException
                | IOException ex) {
            logger.error("不能初始化 SSL context. Cause = {}, errorMessage = {}.", ex.getCause(),
                    ex.getMessage());
            return null;
        }
    }

    private InputStream jksDatastore(String jksPath) throws FileNotFoundException {
        URL jksUrl = getClass().getClassLoader().getResource(jksPath);
        if (jksUrl != null) {
            logger.info("Starting with jks at {}, jks normal {}", jksUrl.toExternalForm(), jksUrl);
            return getClass().getClassLoader().getResourceAsStream(jksPath);
        }

        logger.warn("在resources文件夹没有找到keystore. 扫描外部路径...");
        File jksFile = new File(jksPath);
        if (jksFile.exists()) {
            logger.info("加载外部keystore. Url = {}.", jksFile.getAbsolutePath());
            return new FileInputStream(jksFile);
        }

        logger.warn("keystore文件不存在. Url = {}.", jksFile.getAbsolutePath());
        return null;
    }
}