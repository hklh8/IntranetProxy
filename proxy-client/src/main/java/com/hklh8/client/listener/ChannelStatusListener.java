package com.hklh8.client.listener;

import io.netty.channel.ChannelHandlerContext;

public interface ChannelStatusListener {
    void channelInactive(ChannelHandlerContext ctx);
}
