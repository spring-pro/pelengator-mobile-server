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

package com.pelengator.server.mobile.rest.entity.request.device;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.pelengator.server.mobile.rest.entity.BaseEntity;

public class DeviceSetRequest extends BaseEntity {

    @Since(1.0)
    @SerializedName("secr")
    private Long secr;

    @SerializedName("imei")
    private String imei;

    public Long getSecr() {
        return secr;
    }

    public void setSecr(Long secr) {
        this.secr = secr;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }
}
