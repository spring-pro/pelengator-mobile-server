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
import org.apache.logging.log4j.core.tools.picocli.CommandLine;

public class DeviceDeleteUserRequest extends BaseEntity {

    @Since(1.0)
    @SerializedName("device_imei")
    private Long deviceImei;

    @Since(1.0)
    @SerializedName("phone_num")
    private String phoneNum;

    public Long getDeviceImei() {
        return deviceImei;
    }

    public void setDeviceImei(Long deviceImei) {
        this.deviceImei = deviceImei;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }
}
