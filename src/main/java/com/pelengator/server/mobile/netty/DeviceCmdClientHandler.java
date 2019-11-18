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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.log4j.Logger;

public class DeviceCmdClientHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = Core.getLogger(DeviceCmdClientHandler.class.getSimpleName());

    private TransportCommandResponseObject result;

    DeviceCmdClientHandler(TransportCommandResponseObject result) {
        this.result = result;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        try {
            result = TransportCommandResponseObject.object((String) msg);
        } catch (Throwable cause) {
            LOGGER.error(cause);
            LOGGER.debug(null, cause);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}

