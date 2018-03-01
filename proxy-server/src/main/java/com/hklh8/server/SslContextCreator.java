package com.hklh8.server;

import com.hklh8.server.utils.PropertiesValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;

public class SslContextCreator {

    private static Logger logger = LoggerFactory.getLogger(SslContextCreator.class);

    public SSLContext initSSLContext() {
        logger.info("检查SSL配置属性...");
        final String jksPath = PropertiesValue.getStringValue("ssl.jksPath");
        logger.info("初始化SSL context. KeystorePath = {}.", jksPath);
        if (jksPath == null || jksPath.isEmpty()) {
            logger.warn("keystore路径是null或空。SSL context不会初始化。");
            return null;
        }

        final String keyStorePassword = PropertiesValue.getStringValue("ssl.keyStorePassword");
        final String keyManagerPassword = PropertiesValue.getStringValue("ssl.keyManagerPassword");
        if (keyStorePassword == null || keyStorePassword.isEmpty()) {
            logger.warn("keyManagerPassword是null或空。SSL context不会初始化。");
            return null;
        }

        if (keyManagerPassword == null || keyManagerPassword.isEmpty()) {
            logger.warn("keyManagerPassword是null或空。SSL context不会初始化。");
            return null;
        }

        boolean needsClientAuth = PropertiesValue.getBooleanValue("ssl.needsClientAuth", false);

        try {
            logger.info("加载 keystore. KeystorePath = {}.", jksPath);
            InputStream jksInputStream = jksDatastore(jksPath);
            SSLContext serverContext = SSLContext.getInstance("TLS");
            final KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(jksInputStream, keyStorePassword.toCharArray());
            logger.info("初始化 key manager...");
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyManagerPassword.toCharArray());
            TrustManager[] trustManagers = null;
            if (needsClientAuth) {
                logger.warn("启用客户端身份验证。 keystore将作为信任库。 KeystorePath = {}.", jksPath);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);
                trustManagers = tmf.getTrustManagers();
            }

            //初始化 sslContext
            logger.info("初始化 SSL context...");
            serverContext.init(kmf.getKeyManagers(), trustManagers, null);
            logger.info("SSL context 初始化成功...");

            return serverContext;
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException | KeyStoreException
                | KeyManagementException | IOException ex) {
            logger.error("不能初始化 SSL context. Cause = {}, errorMessage = {}.", ex.getCause(), ex.getMessage());
            return null;
        }
    }

    private InputStream jksDatastore(String jksPath) throws FileNotFoundException {
        URL jksUrl = getClass().getClassLoader().getResource(jksPath);
        if (jksUrl != null) {
            logger.info("Starting with jks at {}, jks normal {}", jksUrl.toExternalForm(), jksUrl);
            return getClass().getClassLoader().getResourceAsStream(jksPath);
        }

        logger.error("在resources文件夹没有找到keystore。 扫描外部路径...");
        File jksFile = new File(jksPath);
        if (jksFile.exists()) {
            logger.info("加载外部keystore。 Url = {}.", jksFile.getAbsolutePath());
            return new FileInputStream(jksFile);
        }

        logger.error("keystore文件不存在。 Url = {}.", jksFile.getAbsolutePath());
        return null;
    }
}