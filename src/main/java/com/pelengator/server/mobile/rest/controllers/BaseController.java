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
import com.pelengator.server.dao.postgresql.entity.Payment;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.kafka.TransportCommandObject;
import com.pelengator.server.mobile.rest.entity.response.BaseCmdResponse;
import com.pelengator.server.utils.ApplicationUtility;
import org.apache.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public abstract class BaseController {

    private static final Logger LOGGER = Core.getLogger(BaseController.class.getSimpleName());

    protected final static String HTTP_SERVER_URL = "https://test-pelengator.com:8443";
    protected final static int PAYMENT_ITEM_STATE_INACTIVE = 0;
    protected final static int PAYMENT_ITEM_STATE_ACTIVE = 1;
    protected final static int PAYMENT_ITEM_STATE_PREPAYMENT = 2;
    protected final static int PAYMENT_ITEM_STATE_NEED_PAY = 3;

    protected final static int PAYMENT_TYPE_CLIENT_ACTIVATION = 1;
    protected final static int PAYMENT_TYPE_CLIENT_TELEMATICS = 2;
    protected final static int PAYMENT_TYPE_CLIENT_PREPAY = 3;
    protected final static int PAYMENT_TYPE_CLIENT_POWER_SUPPORT = 4;
    protected final static int PAYMENT_TYPE_CLIENT_WALLET = 5;

    protected static Gson gson = new Gson();

    static String appKey = "s1dD87sdySPR12M2";

    private Core core_;

    protected BaseCmdResponse sendAutofonCmdPost(TransportCommandObject data) {

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<TransportCommandObject> requestBody = new HttpEntity<>(data);

            ResponseEntity<BaseCmdResponse> result
                    = restTemplate.postForEntity(core_.getGatewayCmdURL(), requestBody, BaseCmdResponse.class);

            return result.getBody();
        } catch (Exception ex) {
            return new BaseCmdResponse(500, ex.getMessage(), null);
        }
    }

    protected int getPayTelematicsFullPeriodDays(Payment paymentTelematics) {
        return paymentTelematics == null ? 0 :
                ((int) (ApplicationUtility.getDateInSecondsWithAddMonthCount(
                        paymentTelematics.getUpdatedAt(), paymentTelematics.getPayPeriodMonths())
                        - ApplicationUtility.getDateInSeconds(paymentTelematics.getUpdatedAt())) / (60 * 60 * 24));
    }

    protected int getPayTelematicsStateDays(Payment paymentTelematics, int payFullPeriodDays) {
        return paymentTelematics == null ? 0 : (payFullPeriodDays -
                ((int) (ApplicationUtility.getDateInSeconds() -
                        ApplicationUtility.getDateInSeconds(paymentTelematics.getUpdatedAt())) / (60 * 60 * 24)));
    }

    public Core getCore_() {
        return core_;
    }

    public void setCore_(Core core_) {
        this.core_ = core_;
    }
}
