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

import java.util.List;
import java.util.Map;

public class DeviceSettingsResponse extends BaseEntity {

    @Since(1.0)
    @SerializedName("buttons")
    private Map<String, List<Map<String, Object>>> buttons;

    @Since(1.0)
    @SerializedName("balance_in_menu")
    private boolean balanceInMenu;

    @Since(1.0)
    @SerializedName("autostart_runtime")
    private Integer autostartRuntime;

    @Since(1.0)
    @SerializedName("all_buttons")
    private List<Map<String, Object>> allButtons;

    public Map<String, List<Map<String, Object>>> getButtons() {
        return buttons;
    }

    public void setButtons(Map<String, List<Map<String, Object>>> buttons) {
        this.buttons = buttons;
    }

    public boolean isBalanceInMenu() {
        return balanceInMenu;
    }

    public void setBalanceInMenu(boolean balanceInMenu) {
        this.balanceInMenu = balanceInMenu;
    }

    public Integer getAutostartRuntime() {
        return autostartRuntime;
    }

    public void setAutostartRuntime(Integer autostartRuntime) {
        this.autostartRuntime = autostartRuntime;
    }

    public List<Map<String, Object>> getAllButtons() {
        return allButtons;
    }

    public void setAllButtons(List<Map<String, Object>> allButtons) {
        this.allButtons = allButtons;
    }
}
