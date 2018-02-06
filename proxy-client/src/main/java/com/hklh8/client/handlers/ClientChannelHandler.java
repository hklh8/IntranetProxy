package com.hklh8.client.handlers;

import com.hklh8.client.ClientChannelMannager;
import com.hklh8.client.listener.ChannelStatusListener;
import com.hklh8.client.listener.ProxyChannelBorrowListener;
import com.hklh8.common.Config;
import com.hklh8.protocol.Constants;
import com.hklh8.protocol.ProxyMessage;
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
        logger.debug("recieved proxy message, type is {}", proxyMessage.getType());
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
            logger.debug("write data to real server, {}", realServerChannel);
            realServerChannel.writeAndFlush(buf);
        }
    }

    private void handleDisconnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        logger.debug("handleDisconnectMessage, {}", realServerChannel);
        if (realServerChannel != null) {
            ctx.channel().attr(Constants.NEXT_CHANNEL).remove();
            ClientChannelMannager.returnProxyChanel(ctx.channel());
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
            // 连接后端服务器成功
            if (future.isSuccess()) {
                final Channel realServerChannel = future.channel();
                logger.debug("connect realserver success, {}", realServerChannel);

                realServerChannel.config().setOption(ChannelOption.AUTO_READ, false);

                // 获取连接
                ClientChannelMannager.borrowProxyChanel(proxyBootstrap, new ProxyChannelBorrowListener() {
                    @Override
                    public void success(Channel channel) {
                        // 连接绑定
                        channel.attr(Constants.NEXT_CHANNEL).set(realServerChannel);
                        realServerChannel.attr(Constants.NEXT_CHANNEL).set(channel);

                        // 远程绑定
                        ProxyMessage proxyMessage1 = new ProxyMessage();
                        proxyMessage1.setType(ProxyMessage.TYPE_CONNECT);
                        proxyMessage1.setUri(userId + "@" + Config.getInstance().getStringValue("client.key"));
                        channel.writeAndFlush(proxyMessage1);

                        realServerChannel.config().setOption(ChannelOption.AUTO_READ, true);
                        ClientChannelMannager.addRealServerChannel(userId, realServerChannel);
                        ClientChannelMannager.setRealServerChannelUserId(realServerChannel, userId);
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
        if (ClientChannelMannager.getCmdChannel() == ctx.channel()) {
            ClientChannelMannager.setCmdChannel(null);
            ClientChannelMannager.clearRealServerChannels();
            channelStatusListener.channelInactive(ctx);
        } else {
            // 数据传输连接
            Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
            if (realServerChannel != null && realServerChannel.isActive()) {
                realServerChannel.close();
            }
        }

        ClientChannelMannager.removeProxyChanel(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exception caught", cause);
        super.exceptionCaught(ctx, cause);
    }

}