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

package com.pelengator.server.mobile.rest.controllers;

import com.google.gson.Gson;
import com.pelengator.server.mobile.Core;
import org.apache.log4j.Logger;

public abstract class BaseController {

    private static final Logger LOGGER = Core.getLogger(BaseController.class.getSimpleName());

    protected static Gson gson = new Gson();

    static String appAndroidKey = "s1dD87sdySPR12M2";

    private Core core_;

    public Core getCore_() {
        return core_;
    }

    public void setCore_(Core core_) {
        this.core_ = core_;
    }
}
