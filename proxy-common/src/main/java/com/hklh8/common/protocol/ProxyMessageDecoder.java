package com.hklh8.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class ProxyMessageDecoder extends LengthFieldBasedFrameDecoder {

    private static final byte HEADER_SIZE = 4;

    private static final int TYPE_SIZE = 1;

    private static final int SERIAL_NUMBER_SIZE = 8;

    private static final int URI_LENGTH_SIZE = 1;

    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    /**
     * @param maxFrameLength 解码时，处理每个帧数据的最大长度
     * @param lengthFieldOffset 该帧数据中，存放该帧数据的长度的数据的起始位置
     * @param lengthFieldLength 记录该帧数据长度的字段本身的长度
     * @param lengthAdjustment 长度调节值，在总长被定义为包含包头长度时，修正信息长度
     * @param initialBytesToStrip 解析的时候需要跳过的字节数
     * @param failFast 为true，当frame长度超过maxFrameLength时立即报TooLongFrameException异常，为false，读取完整个帧再报异常
     */
    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip, failFast);
    }

    @Override
    protected ProxyMessage decode(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        ByteBuf in = (ByteBuf) super.decode(ctx, buf);
        if (in == null) {
            return null;
        }

        if (in.readableBytes() < HEADER_SIZE) {
            return null;
        }

        int frameLength = in.readInt();
        if (in.readableBytes() < frameLength) {
            return null;
        }
        ProxyMessage proxyMessage = new ProxyMessage();
        byte type = in.readByte();
        long sn = in.readLong();

        proxyMessage.setSerialNumber(sn);

        proxyMessage.setType(type);

        byte uriLength = in.readByte();
        byte[] uriBytes = new byte[uriLength];
        in.readBytes(uriBytes);
        proxyMessage.setUri(new String(uriBytes));

        byte[] data = new byte[frameLength - TYPE_SIZE - SERIAL_NUMBER_SIZE - URI_LENGTH_SIZE - uriLength];
        in.readBytes(data);
        proxyMessage.setData(data);

        //释放ByteBuf的引用计数。
        in.release();

        return proxyMessage;
    }
}