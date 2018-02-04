package com.hklh8.client.listener;

import io.netty.channel.Channel;

public interface ProxyChannelBorrowListener {
    void success(Channel channel);

    void error(Throwable cause);
}
