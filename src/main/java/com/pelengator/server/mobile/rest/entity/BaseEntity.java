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

package com.pelengator.server.mobile.rest.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pelengator.server.utils.ApplicationUtility;

public class BaseEntity {

    private static Gson gson = (new GsonBuilder()).create();
    private static Gson gsonV1_0 = (new GsonBuilder()).setVersion(1.0D).create();

    public String json() {
        return gson.toJson(this);
    }

    public String jsonV1_0() {
        return gsonV1_0.toJson(this);
    }

    public static <T> T object(String json, final Class<T> type) {
        return gson.fromJson(json, type);
    }

    public static <T> T objectV1_0(String json, final Class<T> type) {
        return gsonV1_0.fromJson(json, type);
    }

    public String encodedJson(String key) {
        return ApplicationUtility.encrypt(key, gson.toJson(this));
    }

    public String encodedJsonV1_0(String key) {
        return ApplicationUtility.encrypt(key, gsonV1_0.toJson(this));
    }
}
