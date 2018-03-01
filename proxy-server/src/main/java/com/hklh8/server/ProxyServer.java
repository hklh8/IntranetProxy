package com.hklh8.server;

import com.hklh8.common.protocol.IdleCheckHandler;
import com.hklh8.common.protocol.ProxyMessageDecoder;
import com.hklh8.common.protocol.ProxyMessageEncoder;
import com.hklh8.server.config.ProxyConfig;
import com.hklh8.server.handlers.ServerChannelHandler;
import com.hklh8.server.handlers.UserChannelHandler;
import com.hklh8.server.metrics.handler.BytesMetricsHandler;
import com.hklh8.server.utils.PropertiesValue;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.BindException;
import java.util.List;

public class ProxyServer implements ProxyConfig.ConfigChangedListener {

    //max packet is 2M.
    //解码时，处理每个帧数据的最大长度
    private static final int MAX_FRAME_LENGTH = 2 * 1024 * 1024;

    //该帧数据中，存放该帧数据的长度的数据的起始位置
    private static final int LENGTH_FIELD_OFFSET = 0;

    //记录该帧数据长度的字段本身的长度
    private static final int LENGTH_FIELD_LENGTH = 4;

    //解析的时候需要跳过的字节数
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    //长度调节值，在总长被定义为包含包头长度时，修正信息长度，可以为负数
    private static final int LENGTH_ADJUSTMENT = 0;

    private static Logger logger = LoggerFactory.getLogger(ProxyServer.class);

    private NioEventLoopGroup serverWorkerGroup;

    private NioEventLoopGroup serverBossGroup;

    public ProxyServer() {

        serverBossGroup = new NioEventLoopGroup();
        serverWorkerGroup = new NioEventLoopGroup();

        ProxyConfig.getInstance().addConfigChangedListener(this);
    }

    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new ProxyMessageDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP));
                ch.pipeline().addLast(new ProxyMessageEncoder());
                ch.pipeline().addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME, IdleCheckHandler.WRITE_IDLE_TIME, 0));
                ch.pipeline().addLast(new ServerChannelHandler());
            }
        });

        String proxyServerBind = PropertiesValue.getStringValue("proxy.server.bind", "0.0.0.0");
        int proxyServerPort = PropertiesValue.getIntValue("proxy.server.port", 4900);

        try {
            bootstrap.bind(proxyServerBind, proxyServerPort).get();
            logger.info("代理服务器启动，监听端口 " + proxyServerPort);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (PropertiesValue.getBooleanValue("ssl.enable", false)) {
            String host = PropertiesValue.getStringValue("ssl.bind", "0.0.0.0");
            int port = PropertiesValue.getIntValue("ssl.port", 4993);
            initializeSSLTCPTransport(host, port, new SslContextCreator().initSSLContext());
        }

        startUserPort();
    }

    private void initializeSSLTCPTransport(String host, int port, final SSLContext sslContext) {
        ServerBootstrap b = new ServerBootstrap();
        b.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                try {
                    pipeline.addLast("ssl", createSslHandler(sslContext, PropertiesValue.getBooleanValue("ssl.needsClientAuth", false)));
                    ch.pipeline().addLast(new ProxyMessageDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP));
                    ch.pipeline().addLast(new ProxyMessageEncoder());
                    ch.pipeline().addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME, IdleCheckHandler.WRITE_IDLE_TIME, 0));
                    ch.pipeline().addLast(new ServerChannelHandler());
                } catch (Throwable th) {
                    logger.error("创建pipeline时，出现错误", th);
                    throw th;
                }
            }
        });
        try {
            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(host, port);
            f.sync();
            logger.info("SSL服务启动，监听端口 {}", port);
        } catch (InterruptedException ex) {
            logger.error("初始化SSL服务出错", ex);
        }
    }

    private void startUserPort() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addFirst(new BytesMetricsHandler());
                ch.pipeline().addLast(new UserChannelHandler());
            }
        });

        List<Integer> ports = ProxyConfig.getInstance().getUserPorts();
        for (int port : ports) {
            try {
                bootstrap.bind(port).get();
                logger.info("绑定用户端口 " + port);
            } catch (Exception ex) {
                // BindException表示该端口已经绑定过
                if (!(ex.getCause() instanceof BindException)) {
                    throw new RuntimeException(ex);
                }
            }
        }

    }

    @Override
    public void onChanged() {
        startUserPort();
    }

    public void stop() {
        serverBossGroup.shutdownGracefully();
        serverWorkerGroup.shutdownGracefully();
    }

    private ChannelHandler createSslHandler(SSLContext sslContext, boolean needsClientAuth) {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        if (needsClientAuth) {
            sslEngine.setNeedClientAuth(true);
        }

        return new SslHandler(sslEngine);
    }
}
