package com.hklh8.client;

import com.hklh8.client.listener.ProxyChannelBorrowListener;
import com.hklh8.common.protocol.Constants;
import com.hklh8.client.utils.SpringContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 代理客户端与后端真实服务器连接管理
 */
public class ClientChannelManager {

    private static Logger logger = LoggerFactory.getLogger(ClientChannelManager.class);

    private static final AttributeKey<Boolean> USER_CHANNEL_WRITEABLE = AttributeKey.newInstance("user_channel_writeable");

    private static final AttributeKey<Boolean> CLIENT_CHANNEL_WRITEABLE = AttributeKey.newInstance("client_channel_writeable");

    private static final int MAX_POOL_SIZE = 100;

    private static Map<String, Channel> realServerChannels = new ConcurrentHashMap<>();

    private static ConcurrentLinkedQueue<Channel> proxyChannelPool = new ConcurrentLinkedQueue<>();

    private static volatile Channel cmdChannel;

    public static void borrowProxyChanel(Bootstrap bootstrap, final ProxyChannelBorrowListener borrowListener) {
        Channel channel = proxyChannelPool.poll();
        if (channel != null) {
            borrowListener.success(channel);
            return;
        }

        String proxyServerHost = SpringContext.getEnvironment().getProperty("proxy.server.host");
        String proxyServerPort = SpringContext.getEnvironment().getProperty("proxy.server.port");

        bootstrap.connect(proxyServerHost, Integer.parseInt(proxyServerPort)).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                borrowListener.success(future.channel());
            } else {
                logger.warn("连接代理服务器失败", future.cause());
                borrowListener.error(future.cause());
            }
        });
    }

    public static void returnProxyChanel(Channel proxyChanel) {
        if (proxyChannelPool.size() > MAX_POOL_SIZE) {
            proxyChanel.close();
        } else {
            proxyChanel.config().setOption(ChannelOption.AUTO_READ, true);
            proxyChanel.attr(Constants.NEXT_CHANNEL).remove();
            proxyChannelPool.offer(proxyChanel);
            logger.debug("从连接池返回代理服务器连接, channel 为 {}, 连接池大小为 {} ", proxyChanel, proxyChannelPool.size());
        }
    }

    public static void removeProxyChanel(Channel proxyChanel) {
        proxyChannelPool.remove(proxyChanel);
    }

    public static void setCmdChannel(Channel cmdChannel) {
        ClientChannelManager.cmdChannel = cmdChannel;
    }

    public static Channel getCmdChannel() {
        return cmdChannel;
    }

    public static void setRealServerChannelUserId(Channel realServerChannel, String userId) {
        realServerChannel.attr(Constants.USER_ID).set(userId);
    }

    public static String getRealServerChannelUserId(Channel realServerChannel) {
        return realServerChannel.attr(Constants.USER_ID).get();
    }

    public static Channel getRealServerChannel(String userId) {
        return realServerChannels.get(userId);
    }

    public static void addRealServerChannel(String userId, Channel realServerChannel) {
        realServerChannels.put(userId, realServerChannel);
    }

    public static Channel removeRealServerChannel(String userId) {
        return realServerChannels.remove(userId);
    }

    public static boolean isRealServerReadable(Channel realServerChannel) {
        return realServerChannel.attr(CLIENT_CHANNEL_WRITEABLE).get() && realServerChannel.attr(USER_CHANNEL_WRITEABLE).get();
    }

    public static void clearRealServerChannels() {
        logger.warn("channel关闭, 清空目标服务器channels");

        Iterator<Entry<String, Channel>> ite = realServerChannels.entrySet().iterator();
        while (ite.hasNext()) {
            Channel realServerChannel = ite.next().getValue();
            if (realServerChannel.isActive()) {
                realServerChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
        realServerChannels.clear();
    }
}
