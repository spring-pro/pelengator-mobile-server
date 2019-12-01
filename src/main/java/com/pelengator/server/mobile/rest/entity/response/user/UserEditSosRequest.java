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

package com.pelengator.server.mobile.rest.entity.response.user;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.pelengator.server.mobile.rest.entity.BaseEntity;

public class UserEditSosRequest extends BaseEntity {

    @Since(1.0)
    @SerializedName("0")
    private String phoneIndex0;

    @Since(1.0)
    @SerializedName("1")
    private String phoneIndex1;

    @Since(1.0)
    @SerializedName("2")
    private String phoneIndex2;

    public String getPhoneIndex0() {
        return phoneIndex0;
    }

    public void setPhoneIndex0(String phoneIndex0) {
        this.phoneIndex0 = phoneIndex0;
    }

    public String getPhoneIndex1() {
        return phoneIndex1;
    }

    public void setPhoneIndex1(String phoneIndex1) {
        this.phoneIndex1 = phoneIndex1;
    }

    public String getPhoneIndex2() {
        return phoneIndex2;
    }

    public void setPhoneIndex2(String phoneIndex2) {
        this.phoneIndex2 = phoneIndex2;
    }
}
