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

package com.pelengator.server.mobile.kafka;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {

    public static final ByteBuf AUTOFON_CMD_ENGINE_START =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x1C, (byte) 0x0A});

    public static final ByteBuf AUTOFON_CMD_ENGINE_STOP =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x1E, (byte) 0x03});

    public static final ByteBuf AUTOFON_CMD_UNLOCK =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x11, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_LOCK =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x10, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_SERVICE_ENABLE =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x16, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_SERVICE_DISABLE =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x17, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_ENGINE_LOCK =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x19, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_ENGINE_UNLOCK =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x1A, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_ARM_DISABLE =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x0E, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_ARM_ENABLE =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x0A, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_SEARCH_CAR =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x32, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_GET_HARDWARE_INFO =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x33, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_GET_STATUS_AUTO_INFO =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x1D, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_BLOCK =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x37, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_UNBLOCK =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x38, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_ACTIVATION_KIT =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x39, (byte) 0x00});

    public static final ByteBuf AUTOFON_CMD_DEACTIVATION_KIT =
            Unpooled.buffer().writeBytes(new byte[]{
                    (byte) 0xF5, (byte) 0x4A, (byte) 0x12, (byte) 0x3A, (byte) 0x00});

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public void send(String message) {
        kafkaTemplate.send("autofon-cmd-msg", message);
    }
}
