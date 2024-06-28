package com.jd.platform.jlog.client.udp;

import com.jd.platform.jlog.client.Context;
import com.jd.platform.jlog.client.modeholder.ModeHolder;
import com.jd.platform.jlog.client.worker.WorkerInfoHolder;
import com.jd.platform.jlog.common.constant.Constant;
import com.jd.platform.jlog.common.model.TracerData;
import com.jd.platform.jlog.common.utils.AsyncPool;
import com.jd.platform.jlog.common.utils.ProtostuffUtils;
import com.jd.platform.jlog.common.utils.ZstdUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * UdpClient
 * @author wuweifeng
 * @version 1.0
 * @date 2021-08-10
 */
public class UdpClient {

    /**
     * 大日志即超过6w行
     */
    private static final long COMPRESS_BYTES_LEN = 60000L;

    /**
     * 启动udp客户端
     */
    public void start() {
        AsyncPool.asyncDo(this::startUdp);
    }

    /**
     * startUdp
     */
    private void startUdp() {
        //1.NioEventLoopGroup是执行者
        NioEventLoopGroup group = new NioEventLoopGroup();
        //2.启动器
        Bootstrap bootstrap = new Bootstrap();
        //3.配置启动器
        bootstrap.group(group)                         //3.1指定group
                .channel(NioDatagramChannel.class)     //3.2指定channel
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                        //3.4在pipeline中加入编码器，和解码器（用来处理返回的消息）
                        nioDatagramChannel.pipeline().addLast(new MyUdpEncoder());
                    }
                });
        //4.bind并返回一个channel
        try {
            Channel channel = bootstrap.bind(8887).sync().channel();
            Context.CHANNEL = channel;

            //6.等待channel的close
            channel.closeFuture().sync();
            //7.关闭group
            group.shutdownGracefully();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 编码器，将要发送的消息（这里是一个String）封装到一个DatagramPacket中
     */
    private static class MyUdpEncoder extends MessageToMessageEncoder<TracerData> {
        @Override
        protected void encode(ChannelHandlerContext channelHandlerContext, TracerData tracerData, List<Object> list) {
            // 序列化
            byte[] bytes = ProtostuffUtils.serialize(tracerData);
            // 压缩
            byte[] compressBytes = ZstdUtils.compress(bytes);

            //判断压缩完是否过大，过大走http接口请求worker
            if (compressBytes.length >= COMPRESS_BYTES_LEN) {
                //放入发okhttp的队列
                HttpSender.offerBean(compressBytes,tracerData.getAddress());
                return;
            }

            ByteBuf buf = channelHandlerContext.alloc().buffer(compressBytes.length);
            buf.writeBytes(compressBytes);
            InetSocketAddress remoteAddress=null;
            // 单播
            if(ModeHolder.getSendMode().getUnicast()){
                // 轮训挑选一个worker
                String workerIpPort =  WorkerInfoHolder.chooseWorker();
                if (workerIpPort == null) {
                    return;
                }
                String[] ipPort = workerIpPort.split(Constant.SPLITER);
                //发往worker
                remoteAddress= new InetSocketAddress(ipPort[0], Integer.parseInt(ipPort[1]));
            }else{
                remoteAddress=tracerData.getAddress();
            }
            DatagramPacket packet = new DatagramPacket(buf, remoteAddress);
            list.add(packet);
        }
    }

}
