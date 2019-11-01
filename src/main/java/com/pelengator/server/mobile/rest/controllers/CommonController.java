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

import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.entity.response.common.VersionResponse;
import org.apache.log4j.Logger;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class CommonController extends BaseController {

    private static final Logger LOGGER = Core.getLogger(CommonController.class.getSimpleName());

    @RequestMapping(value = "/check_versions", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public @ResponseBody
    BaseResponse getVersion() {

        //TODO Process request data ...

        VersionResponse data = new VersionResponse();
        data.setAndroidApp("1.2.3.180604");
        data.setIosApp("1.2.3.180604");
        return new BaseResponse(200, "", data);
    }
}
