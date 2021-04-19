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

package com.pelengator.server.mobile.rest.entity.request.device;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.pelengator.server.mobile.rest.entity.BaseEntity;

public class DeviceChangeUserRequest extends BaseEntity {

    @Since(1.0)
    @SerializedName("device_imei")
    private Long deviceImei;

    @Since(1.0)
    @SerializedName("new_phone_num")
    private String newPhoneNum;

    @Since(1.0)
    @SerializedName("old_phone_num")
    private String oldPhoneNum;

    @Since(1.0)
    @SerializedName("sms_code")
    private String smsCode;

    public Long getDeviceImei() {
        return deviceImei;
    }

    public void setDeviceImei(Long deviceImei) {
        this.deviceImei = deviceImei;
    }

    public String getNewPhoneNum() {
        return newPhoneNum;
    }

    public void setNewPhoneNum(String newPhoneNum) {
        this.newPhoneNum = newPhoneNum;
    }

    public String getOldPhoneNum() {
        return oldPhoneNum;
    }

    public void setOldPhoneNum(String oldPhoneNum) {
        this.oldPhoneNum = oldPhoneNum;
    }

    public String getSmsCode() {
        return smsCode;
    }

    public void setSmsCode(String smsCode) {
        this.smsCode = smsCode;
    }
}
