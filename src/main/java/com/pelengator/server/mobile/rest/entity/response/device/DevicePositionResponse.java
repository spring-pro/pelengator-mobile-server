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

public class DevicePositionResponse extends BaseEntity {

    @Since(1.0)
    @SerializedName("pos_update_ts")
    private long positionUpdatedAt;

    @Since(1.0)
    @SerializedName("lat")
    private double lat;

    @Since(1.0)
    @SerializedName("lng")
    private double lng;

    @Since(1.0)
    @SerializedName("speed")
    private int speed;

    @Since(1.0)
    @SerializedName("accuracy")
    private int accuracy;

    public long getPositionUpdatedAt() {
        return positionUpdatedAt;
    }

    public void setPositionUpdatedAt(long positionUpdatedAt) {
        this.positionUpdatedAt = positionUpdatedAt;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(int accuracy) {
        this.accuracy = accuracy;
    }
}
