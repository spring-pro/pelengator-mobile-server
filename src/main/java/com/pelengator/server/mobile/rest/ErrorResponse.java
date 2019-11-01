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

package com.pelengator.server.mobile.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pelengator.server.utils.ApplicationUtility;

@SuppressWarnings("UnusedDeclaration")
public class ErrorResponse {

    private static Gson gson = (new GsonBuilder()).create();
    private static Gson gsonV1_0 = (new GsonBuilder()).setVersion(1.0D).create();

    private int code;
    private String message;

    public ErrorResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String json() {
        return gson.toJson(this);
    }

    public String jsonV1_0() {
        return gsonV1_0.toJson(this);
    }

    public String encodedJson(String key) {
        return ApplicationUtility.encrypt(key, gson.toJson(this));
    }

    public String encodedJsonV1_0(String key) {
        return ApplicationUtility.encrypt(key, gsonV1_0.toJson(this));
    }
}
