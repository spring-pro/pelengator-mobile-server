/*
 * Copyright (c) 2019 Spring-Pro
 * Moscow, Russia
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of Spring-Pro. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Spring-Pro.
 *
 * Author: Maxim Zemskov, https://www.linkedin.com/in/mzemskov/
 */

package com.pelengator.server.mobile.netty;

import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.kafka.TransportCommandObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.apache.log4j.Logger;

import java.nio.charset.StandardCharsets;

import static org.apache.log4j.Logger.getLogger;

public class DeviceCmdClient {

    private static final Logger LOGGER = getLogger(DeviceCmdClient.class.getSimpleName());

    private Core core;
    private ChannelFuture channelFuture;

    public TransportCommandResponseObject send(String serverAddress, int serverPort,
                                               TransportCommandObject commandObject) throws Throwable {
        try {
            TransportCommandResponseObject result =
                    new TransportCommandResponseObject(commandObject.getImei(), "");

            EpollEventLoopGroup workerGroup = new EpollEventLoopGroup();

            try {
                Bootstrap b = new Bootstrap();
                b.group(workerGroup);
                b.channel(NioSocketChannel.class);
                b.option(ChannelOption.SO_KEEPALIVE, true);
                b.option(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);

                b.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new StringDecoder(StandardCharsets.ISO_8859_1));
                        ch.pipeline().addLast(new DeviceCmdClientHandler(result));
                        ch.pipeline().addLast(new StringEncoder(StandardCharsets.ISO_8859_1));
                    }
                });

                channelFuture = b.connect(serverAddress, serverPort).sync();
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (channelFuture.isSuccess()) {
                            future.channel().writeAndFlush(commandObject.json());
                        } else channelFuture.channel().close();
                    }
                });

                channelFuture.channel().closeFuture().sync();
            } finally {
                workerGroup.shutdownGracefully();
            }

            return result;
        } catch (Throwable cause) {
            LOGGER.error("TCP DEVICE CMD Server -> ", cause);
            throw cause;
        }
    }
}
