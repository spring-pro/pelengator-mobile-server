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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;

public class TransportCommandResponseObject implements Serializable {

    private static Gson gson = (new GsonBuilder()).create();

    private long imei;
    private String status;

    public TransportCommandResponseObject(long imei, String status) {
        this.imei = imei;
        this.status = status;
    }

    public String json() {
        return gson.toJson(this);
    }

    public static TransportCommandResponseObject object(String json) {
        return gson.fromJson(json, TransportCommandResponseObject.class);
    }

    public long getImei() {
        return imei;
    }

    public void setImei(long imei) {
        this.imei = imei;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
