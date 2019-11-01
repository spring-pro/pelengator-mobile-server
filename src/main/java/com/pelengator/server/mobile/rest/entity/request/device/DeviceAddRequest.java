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

public class DeviceAddRequest extends BaseEntity {

    @Since(1.0)
    @SerializedName("marka")
    private String carBrand;

    @Since(1.0)
    @SerializedName("model")
    private String carModel;

    @Since(1.0)
    @SerializedName("gosnomer")
    private String carNumber;

    @Since(1.0)
    @SerializedName("device_imei")
    private Long deviceImei;

    public String getCarBrand() {
        return carBrand;
    }

    public void setCarBrand(String carBrand) {
        this.carBrand = carBrand;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public String getCarNumber() {
        return carNumber;
    }

    public void setCarNumber(String carNumber) {
        this.carNumber = carNumber;
    }

    public Long getDeviceImei() {
        return deviceImei;
    }

    public void setDeviceImei(Long deviceImei) {
        this.deviceImei = deviceImei;
    }
}
