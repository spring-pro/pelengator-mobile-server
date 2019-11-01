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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserGetConfigResponse extends BaseEntity {

    @Since(1.0)
    @SerializedName("alarm_devices")
    private List<Map> alarmDevices = new ArrayList<>(3);

    @Since(1.0)
    @SerializedName("sos_phones")
    private List<String> sosPhones = new ArrayList<>(3);

    public List<Map> getAlarmDevices() {
        return alarmDevices;
    }

    public void setAlarmDevices(List<Map> alarmDevices) {
        this.alarmDevices = alarmDevices;
    }

    public List<String> getSosPhones() {
        return sosPhones;
    }

    public void setSosPhones(List<String> sosPhones) {
        this.sosPhones = sosPhones;
    }
}
