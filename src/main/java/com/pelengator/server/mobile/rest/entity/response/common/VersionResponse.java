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

package com.pelengator.server.mobile.rest.entity.response.common;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.pelengator.server.mobile.rest.entity.BaseEntity;

public class VersionResponse extends BaseEntity {

    @Since(1.0)
    @SerializedName("android_app")
    private String androidApp;

    @Since(1.0)
    @SerializedName("ios_app")
    private String iosApp;

    public String getAndroidApp() {
        return androidApp;
    }

    public void setAndroidApp(String androidApp) {
        this.androidApp = androidApp;
    }

    public String getIosApp() {
        return iosApp;
    }

    public void setIosApp(String iosApp) {
        this.iosApp = iosApp;
    }
}
