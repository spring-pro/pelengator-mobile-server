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

package com.pelengator.server.mobile.rest.entity.request.user;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.pelengator.server.mobile.rest.entity.BaseEntity;

public class ConfirmRequest extends BaseEntity {

    @Since(1.0)
    @SerializedName("user_id")
    private Long userId;

    @Since(1.0)
    @SerializedName("sms_code")
    private Integer smsCode;

    @Since(1.0)
    @SerializedName("fms_id")
    private Long fmsId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getSmsCode() {
        return smsCode;
    }

    public void setSmsCode(Integer smsCode) {
        this.smsCode = smsCode;
    }

    public Long getFmsId() {
        return fmsId;
    }

    public void setFmsId(Long fmsId) {
        this.fmsId = fmsId;
    }
}
