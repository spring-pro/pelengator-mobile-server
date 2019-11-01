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

package com.pelengator.server.mobile.rest.entity.response.device;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.pelengator.server.mobile.rest.entity.BaseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeviceStateResponse extends BaseEntity {

    @Since(1.0)
    @SerializedName("data_ts")
    private Integer dataTs;

    @Since(1.0)
    @SerializedName("buttons")
    private Map<String, List<Map<String, Object>>> buttons;

    @Since(1.0)
    @SerializedName("test_status")
    private Map<String, Object> testStatus;

    @Since(1.0)
    @SerializedName("all_statuses")
    private Map<String, Object> allStatuses;

    @Since(1.0)
    @SerializedName("messages")
    private List<Object> messages = new ArrayList<>(0);

    public Integer getDataTs() {
        return dataTs;
    }

    public void setDataTs(Integer dataTs) {
        this.dataTs = dataTs;
    }

    public Map<String, List<Map<String, Object>>> getButtons() {
        return buttons;
    }

    public void setButtons(Map<String, List<Map<String, Object>>> buttons) {
        this.buttons = buttons;
    }

    public Map<String, Object> getTestStatus() {
        return testStatus;
    }

    public void setTestStatus(Map<String, Object> testStatus) {
        this.testStatus = testStatus;
    }

    public Map<String, Object> getAllStatuses() {
        return allStatuses;
    }

    public void setAllStatuses(Map<String, Object> allStatuses) {
        this.allStatuses = allStatuses;
    }

    public List<Object> getMessages() {
        return messages;
    }

    public void setMessages(List<Object> messages) {
        this.messages = messages;
    }
}
