package com.hklh8.server.handlers;

import com.hklh8.common.protocol.Constants;
import com.hklh8.common.protocol.ProxyMessage;
import com.hklh8.server.ProxyChannelManager;
import com.hklh8.server.config.ProxyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ServerChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    private static Logger logger = LoggerFactory.getLogger(ServerChannelHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage proxyMessage) throws Exception {
        logger.debug("代理服务器接收消息 {}", proxyMessage.getType());
        switch (proxyMessage.getType()) {
            case ProxyMessage.TYPE_HEARTBEAT:   // 心跳消息
                handleHeartbeatMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.C_TYPE_AUTH:      // 认证消息，检测clientKey是否正确
                handleAuthMessage(ctx, proxyMessage);
                break;
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
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null) {
            ByteBuf buf = ctx.alloc().buffer(proxyMessage.getData().length);
            buf.writeBytes(proxyMessage.getData());
            userChannel.writeAndFlush(buf);
        }
    }

    private void handleDisconnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).get();

        // 代理连接没有连上服务器由控制连接发送用户端断开连接消息
        if (clientKey == null) {
            String userId = proxyMessage.getUri();
            Channel userChannel = ProxyChannelManager.removeUserChannelFromCmdChannel(ctx.channel(), userId);
            if (userChannel != null) {
                // 数据发送完成后再关闭连接，解决http1.0数据传输问题
                userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
            return;
        }

        Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);
        if (cmdChannel == null) {
            logger.warn("错误的clientKey {}", ctx.channel().attr(Constants.CLIENT_KEY).get());
            return;
        }

        Channel userChannel = ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, ctx.channel().attr(Constants.USER_ID).get());
        if (userChannel != null) {
            // 数据发送完成后再关闭连接，解决http1.0数据传输问题
            userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            ctx.channel().attr(Constants.NEXT_CHANNEL).remove();
            ctx.channel().attr(Constants.CLIENT_KEY).remove();
            ctx.channel().attr(Constants.USER_ID).remove();
        }
    }

    private void handleConnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String uri = proxyMessage.getUri();
        if (uri == null) {
            ctx.channel().close();
            logger.warn("uri字段为空...");
            return;
        }

        String[] tokens = uri.split("@");
        if (tokens.length != 2) {
            ctx.channel().close();
            logger.warn("uri格式错误...");
            return;
        }

        Channel cmdChannel = ProxyChannelManager.getCmdChannel(tokens[1]);
        if (cmdChannel == null) {
            ctx.channel().close();
            logger.warn("错误的clientKey {}", tokens[1]);
            return;
        }

        Channel userChannel = ProxyChannelManager.getUserChannel(cmdChannel, tokens[0]);
        if (userChannel != null) {
            ctx.channel().attr(Constants.USER_ID).set(tokens[0]);
            ctx.channel().attr(Constants.CLIENT_KEY).set(tokens[1]);
            ctx.channel().attr(Constants.NEXT_CHANNEL).set(userChannel);
            userChannel.attr(Constants.NEXT_CHANNEL).set(ctx.channel());
            // 代理客户端与后端服务器连接成功，修改用户连接为可读状态
            userChannel.config().setOption(ChannelOption.AUTO_READ, true);
        }
    }

    private void handleHeartbeatMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        ProxyMessage heartbeatMessage = new ProxyMessage();
        heartbeatMessage.setType(ProxyMessage.TYPE_HEARTBEAT);
        logger.debug("响应心跳消息 {}", ctx.channel());
        ctx.channel().writeAndFlush(heartbeatMessage);
    }

    private void handleAuthMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String clientKey = proxyMessage.getUri();   //获取客户端key
        List<Integer> ports = ProxyConfig.getInstance().getClientInetPorts(clientKey);
        if (ports == null) {
            logger.info("错误的clientKey {}, {}", clientKey, ctx.channel());
            ctx.channel().close();
            return;
        }

        Channel channel = ProxyChannelManager.getCmdChannel(clientKey);
        if (channel != null) {
            logger.warn("clientKey {} 存在channel {}", clientKey, channel);
            ctx.channel().close();
            return;
        }

        logger.info("set port => channel, {}, {}, {}", clientKey, ports, ctx.channel());
        ProxyChannelManager.addCmdChannel(ports, clientKey, ctx.channel());
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null) {
            userChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }

        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null && userChannel.isActive()) {
            String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).get();
            String userId = ctx.channel().attr(Constants.USER_ID).get();
            Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);
            if (cmdChannel != null) {
                ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, userId);
            } else {
                logger.warn("cmdChannel为null, clientKey为 {}", clientKey);
            }

            // 数据发送完成后再关闭连接，解决http1.0数据传输问题
            userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            userChannel.close();
        } else {
            ProxyChannelManager.removeCmdChannel(ctx.channel());
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("异常捕获", cause);
        super.exceptionCaught(ctx, cause);
    }
}