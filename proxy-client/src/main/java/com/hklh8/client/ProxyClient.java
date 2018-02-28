package com.hklh8.client;

import com.hklh8.client.handlers.ClientChannelHandler;
import com.hklh8.client.handlers.RealServerChannelHandler;
import com.hklh8.client.listener.ChannelStatusListener;
import com.hklh8.common.protocol.IdleCheckHandler;
import com.hklh8.common.protocol.ProxyMessage;
import com.hklh8.common.protocol.ProxyMessageDecoder;
import com.hklh8.common.protocol.ProxyMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

@Service
public class ProxyClient implements ChannelStatusListener {

    private static Logger logger = LoggerFactory.getLogger(ProxyClient.class);

    @Value("${ssl.enable:false}")
    private boolean sslEnable;

    @Value("${client.key}")
    private String clientKey;

    @Value("${proxy.server.host}")
    private String proxyServerHost;

    @Value("${proxy.server.port}")
    private int proxyServerPort;


    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    private static final int LENGTH_FIELD_OFFSET = 0;

    private static final int LENGTH_FIELD_LENGTH = 4;

    private static final int INITIAL_BYTES_TO_STRIP = 0;

    private static final int LENGTH_ADJUSTMENT = 0;

    private NioEventLoopGroup workerGroup;

    private Bootstrap bootstrap;

    private Bootstrap realServerBootstrap;

    private SSLContext sslContext;

    private long sleepTimeMill = 1000;

    public ProxyClient() {
        workerGroup = new NioEventLoopGroup();
        realServerBootstrap = new Bootstrap();
        realServerBootstrap.group(workerGroup);
        realServerBootstrap.channel(NioSocketChannel.class);
        realServerBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new RealServerChannelHandler());
            }
        });

        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                if (sslEnable) {
                    if (sslContext == null) {
                        sslContext = SslContextCreator.createSSLContext();
                    }
                    ch.pipeline().addLast(createSslHandler(sslContext));
                }
                ch.pipeline().addLast(new ProxyMessageDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP));
                ch.pipeline().addLast(new ProxyMessageEncoder());
                ch.pipeline().addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME, IdleCheckHandler.WRITE_IDLE_TIME - 10, 0));
                ch.pipeline().addLast(new ClientChannelHandler(realServerBootstrap, bootstrap, ProxyClient.this));
            }
        });
    }

    public void start() {
        connectProxyServer();
    }

    private ChannelHandler createSslHandler(SSLContext sslContext) {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(true);
        return new SslHandler(sslEngine);
    }

    private void connectProxyServer() {
        bootstrap.connect(proxyServerHost, proxyServerPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                // 连接成功，向服务器发送客户端认证信息（clientKey）
                ClientChannelManager.setCmdChannel(future.channel());
                ProxyMessage proxyMessage = new ProxyMessage();
                proxyMessage.setType(ProxyMessage.C_TYPE_AUTH);
                proxyMessage.setUri(clientKey);
                future.channel().writeAndFlush(proxyMessage);
                sleepTimeMill = 1000;
                logger.info("连接代理服务器成功, {}", future.channel());
            } else {
                logger.warn("连接代理服务器失败, {}", future.cause());

                // 连接失败，发起重连
                reconnectWait();
                connectProxyServer();
            }
        });
    }

    public void stop() {
        workerGroup.shutdownGracefully();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        reconnectWait();
        connectProxyServer();
    }

    private void reconnectWait() {
        try {
            if (sleepTimeMill > 60000) {
                sleepTimeMill = 1000;
            }

            synchronized (this) {
                sleepTimeMill = sleepTimeMill * 2;
                wait(sleepTimeMill);
            }
        } catch (InterruptedException e) {
        }
    }
}
