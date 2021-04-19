/*
 * Copyright (c) 2021 Spring-Pro
 * Moscow, Russia
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of Spring-Pro. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Spring-Pro.
 *
 * Author: Maxim Zemskov, https://www.linkedin.com/in/mzemskov/
 */

package com.pelengator.server.mobile.dto;

import com.google.gson.Gson;

public class ChangeUserSmsMapCacheL3Object {
    private static Gson gson = new Gson();

    private String newUserPhone;
    private String oldUserPhone;
    private String smsCode;

    public static String getJson(String oldUserPhone, String newUserPhone, String smsCode) {
        ChangeUserSmsMapCacheL3Object instance = new ChangeUserSmsMapCacheL3Object();
        instance.setOldUserPhone(oldUserPhone);
        instance.setNewUserPhone(newUserPhone);
        instance.setSmsCode(smsCode);

        return gson.toJson(instance);
    }

    public static ChangeUserSmsMapCacheL3Object getInstance(String json) {
        return gson.fromJson(json, ChangeUserSmsMapCacheL3Object.class);
    }

    public String getNewUserPhone() {
        return newUserPhone;
    }

    public void setNewUserPhone(String newUserPhone) {
        this.newUserPhone = newUserPhone;
    }

    public String getOldUserPhone() {
        return oldUserPhone;
    }

    public void setOldUserPhone(String oldUserPhone) {
        this.oldUserPhone = oldUserPhone;
    }

    public String getSmsCode() {
        return smsCode;
    }

    public void setSmsCode(String smsCode) {
        this.smsCode = smsCode;
    }
}
