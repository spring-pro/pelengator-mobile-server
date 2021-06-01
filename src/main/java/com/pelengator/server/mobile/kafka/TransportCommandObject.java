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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TransportCommandObject {

    private static Gson gson = (new GsonBuilder()).create();

    private Long imei;
    private Long commandHistoryId;
    private String command;

    public TransportCommandObject(Long imei, Long commandHistoryId, String command) {
        this.imei = imei;
        this.commandHistoryId = commandHistoryId;
        this.command = command;
    }

    public String json() {
        return gson.toJson(this);
    }

    public static TransportCommandObject object(String json) {
        return gson.fromJson(json, TransportCommandObject.class);
    }

    public long getImei() {
        return imei;
    }

    public void setImei(Long imei) {
        this.imei = imei;
    }

    public long getCommandHistoryId() {
        return commandHistoryId;
    }

    public void setCommandHistoryId(Long commandHistoryId) {
        this.commandHistoryId = commandHistoryId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
