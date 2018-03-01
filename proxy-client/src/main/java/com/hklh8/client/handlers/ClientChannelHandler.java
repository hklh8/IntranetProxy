package com.hklh8.client.handlers;

import com.hklh8.client.ClientChannelManager;
import com.hklh8.client.listener.ChannelStatusListener;
import com.hklh8.client.listener.ProxyChannelBorrowListener;
import com.hklh8.client.utils.PropertiesValue;
import com.hklh8.common.protocol.Constants;
import com.hklh8.common.protocol.ProxyMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    private static Logger logger = LoggerFactory.getLogger(ClientChannelHandler.class);

    private Bootstrap bootstrap;

    private Bootstrap proxyBootstrap;

    private ChannelStatusListener channelStatusListener;

    public ClientChannelHandler(Bootstrap bootstrap, Bootstrap proxyBootstrap, ChannelStatusListener channelStatusListener) {
        this.bootstrap = bootstrap;
        this.proxyBootstrap = proxyBootstrap;
        this.channelStatusListener = channelStatusListener;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage proxyMessage) throws Exception {
        logger.debug("接收代理服务器消息, 消息类型为 {}", proxyMessage.getType());
        switch (proxyMessage.getType()) {
            case ProxyMessage.TYPE_CONNECT:     // 代理后端服务器建立连接消息
                handleConnectMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.TYPE_DISCONNECT:  // 代理后端服务器断开连接消息
                handleDisconnectMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.P_TYPE_TRANSFER:  // 代理数据传输
                handleTransferMessage(ctx, proxyMessage);
                break;
            default:
                break;
        }
    }

    private void handleTransferMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (realServerChannel != null) {
            ByteBuf buf = ctx.alloc().buffer(proxyMessage.getData().length);
            buf.writeBytes(proxyMessage.getData());
            logger.debug("发送数据到目标服务器, {}", realServerChannel);
            realServerChannel.writeAndFlush(buf);
        }
    }

    private void handleDisconnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        logger.debug("处理用户连接断开, {}", realServerChannel);
        if (realServerChannel != null) {
            ctx.channel().attr(Constants.NEXT_CHANNEL).remove();
            ClientChannelManager.returnProxyChanel(ctx.channel());
            realServerChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void handleConnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        final Channel cmdChannel = ctx.channel();
        final String userId = proxyMessage.getUri();
        String[] serverInfo = new String(proxyMessage.getData()).split(":");
        String ip = serverInfo[0];
        int port = Integer.parseInt(serverInfo[1]);
        bootstrap.connect(ip, port).addListener((ChannelFutureListener) future -> {
            // 连接目标服务器成功
            if (future.isSuccess()) {
                final Channel realServerChannel = future.channel();
                logger.debug("连接目标服务器成功, {}", realServerChannel);

                realServerChannel.config().setOption(ChannelOption.AUTO_READ, false);

                // 获取连接
                ClientChannelManager.borrowProxyChanel(proxyBootstrap, new ProxyChannelBorrowListener() {
                    @Override
                    public void success(Channel channel) {
                        // 连接绑定
                        channel.attr(Constants.NEXT_CHANNEL).set(realServerChannel);
                        realServerChannel.attr(Constants.NEXT_CHANNEL).set(channel);

                        // 远程绑定
                        ProxyMessage proxyMessage1 = new ProxyMessage();
                        proxyMessage1.setType(ProxyMessage.TYPE_CONNECT);
                        proxyMessage1.setUri(userId + "@" + PropertiesValue.getStringValue("client.key"));
                        channel.writeAndFlush(proxyMessage1);

                        realServerChannel.config().setOption(ChannelOption.AUTO_READ, true);
                        ClientChannelManager.addRealServerChannel(userId, realServerChannel);
                        ClientChannelManager.setRealServerChannelUserId(realServerChannel, userId);
                    }

                    @Override
                    public void error(Throwable cause) {
                        ProxyMessage proxyMessage1 = new ProxyMessage();
                        proxyMessage1.setType(ProxyMessage.TYPE_DISCONNECT);
                        proxyMessage1.setUri(userId);
                        cmdChannel.writeAndFlush(proxyMessage1);
                    }
                });

            } else {
                ProxyMessage proxyMessage1 = new ProxyMessage();
                proxyMessage1.setType(ProxyMessage.TYPE_DISCONNECT);
                proxyMessage1.setUri(userId);
                cmdChannel.writeAndFlush(proxyMessage1);
            }
        });
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (realServerChannel != null) {
            realServerChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 控制连接
        if (ClientChannelManager.getCmdChannel() == ctx.channel()) {
            ClientChannelManager.setCmdChannel(null);
            ClientChannelManager.clearRealServerChannels();
            channelStatusListener.channelInactive(ctx);
        } else {
            // 数据传输连接
            Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
            if (realServerChannel != null && realServerChannel.isActive()) {
                realServerChannel.close();
            }
        }

        ClientChannelManager.removeProxyChanel(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("异常捕获", cause);
        super.exceptionCaught(ctx, cause);
    }

}