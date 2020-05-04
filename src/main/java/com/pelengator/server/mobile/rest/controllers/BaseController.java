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
import com.google.gson.reflect.TypeToken;
import com.pelengator.server.autofon.AutofonCommands;
import com.pelengator.server.dao.postgresql.dto.DialogMessageMobileEntity;
import com.pelengator.server.dao.postgresql.entity.*;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.kafka.TransportCommandObject;
import com.pelengator.server.mobile.rest.entity.response.BaseCmdResponse;
import com.pelengator.server.utils.ApplicationUtility;
import com.pelengator.server.utils.DeviceLogger;
import org.apache.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    protected int getSmsCode(User user) {
        int smsCode = 1234;
        if (!user.getPhone().equals("71111111117") &&
                !user.getPhone().equals("79857777766") && !user.getPhone().equals("79653684111"))
            smsCode = ApplicationUtility.generateRandomInt(1000, 9999);
        LOGGER.debug("SMS code: " + smsCode + " for user: " + user.getPhone());
        return smsCode;
    }

    protected Dialog createDialog(long uid, boolean isReadBySupport) throws Exception {
        Dialog dialog = new Dialog();
        dialog.setUserId(uid);
        dialog.setReadBySupport(isReadBySupport);
        dialog.setCreatedAt(new Timestamp(new Date().getTime()));
        dialog.setUpdatedAt(new Timestamp(new Date().getTime()));
        this.getCore_().getDao().save(dialog);
        return dialog;
    }

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

    protected void sendAutoStatusCmd(Device device, DeviceState deviceState) {

        try {
            DeviceLog deviceLog = new DeviceLog();
            deviceLog.setDeviceId(device.getId());
            deviceLog.setAdminId(null);
            deviceLog.setUserId(null);
            deviceLog.setSenderType(DeviceLog.CommandSenderTypeEnum.SERVER.name());
            deviceLog.setLogType(DeviceLogger.LOG_TYPE_OUTPUT_EVENT);
            deviceLog.setEventType(0);
            deviceLog.setMessage("carInfo");
            deviceLog.setDescription("");
            deviceLog.setErrMsg("");
            deviceLog.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
            deviceLog.setUpdatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
            this.getCore_().getDao().save(deviceLog);

            BaseCmdResponse response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                    AutofonCommands.AUTOFON_CMD_GET_STATUS_AUTO_INFO.toString(StandardCharsets.ISO_8859_1)));

            if (response.getCode() == HttpStatus.OK.value()) {
                deviceLog.setSent(true);
            } else {
                deviceLog.setSent(false);
            }

            this.getCore_().getDao().save(deviceLog);

            if (deviceState != null && !deviceState.isSpr4() && !deviceState.isSpr7() && !deviceState.isSpr12() && !deviceState.isSpr15() &&
                    !deviceState.isT5() && !deviceState.isT15()) {

                deviceLog = new DeviceLog();
                deviceLog.setDeviceId(device.getId());
                deviceLog.setAdminId(null);
                deviceLog.setUserId(null);
                deviceLog.setSenderType(DeviceLog.CommandSenderTypeEnum.SERVER.name());
                deviceLog.setLogType(DeviceLogger.LOG_TYPE_OUTPUT_EVENT);
                deviceLog.setEventType(0);
                deviceLog.setMessage("complexInfo");
                deviceLog.setDescription("");
                deviceLog.setErrMsg("");
                deviceLog.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                deviceLog.setUpdatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                this.getCore_().getDao().save(deviceLog);

                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_GET_HARDWARE_INFO.toString(StandardCharsets.ISO_8859_1)));

                if (response.getCode() == HttpStatus.OK.value())
                    deviceLog.setSent(true);
                else
                    deviceLog.setSent(false);

                this.getCore_().getDao().save(deviceLog);
            }
        } catch (Throwable cause) {
            LOGGER.error("SERVER CMD -> sendAutoStatusCmd: ", cause);
        }
    }

    protected void addDeviceUserHistory(String cmd, long deviceId) {
        try {
            DeviceUserHistory deviceUserHistory = new DeviceUserHistory();
            deviceUserHistory.setDeviceId(deviceId);
            deviceUserHistory.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));

            switch (cmd) {
                case "engine_on": {
                    deviceUserHistory.setGroup_id(DeviceUserHistory.GROUP_4_ID);
                    deviceUserHistory.setTitle(DeviceUserHistory.GROUP_4_TITLE);
                    deviceUserHistory.setMessage(DeviceUserHistory.GROUP_4_EVENT_ENGINE_ON);
                    this.getCore_().getDao().save(deviceUserHistory);
                    break;
                }
                case "engine_off": {
                    deviceUserHistory.setGroup_id(DeviceUserHistory.GROUP_4_ID);
                    deviceUserHistory.setTitle(DeviceUserHistory.GROUP_4_TITLE);
                    deviceUserHistory.setMessage(DeviceUserHistory.GROUP_4_EVENT_ENGINE_OFF);
                    this.getCore_().getDao().save(deviceUserHistory);
                    break;
                }
                case "service_on": {
                    deviceUserHistory.setGroup_id(DeviceUserHistory.GROUP_3_ID);
                    deviceUserHistory.setTitle(DeviceUserHistory.GROUP_3_TITLE);
                    deviceUserHistory.setMessage(DeviceUserHistory.GROUP_3_EVENT_SERVICE_ON);
                    this.getCore_().getDao().save(deviceUserHistory);
                    break;
                }
                case "service_off": {
                    deviceUserHistory.setGroup_id(DeviceUserHistory.GROUP_3_ID);
                    deviceUserHistory.setTitle(DeviceUserHistory.GROUP_3_TITLE);
                    deviceUserHistory.setMessage(DeviceUserHistory.GROUP_3_EVENT_SERVICE_OFF);
                    this.getCore_().getDao().save(deviceUserHistory);
                    break;
                }
                case "arm_on": {
                    deviceUserHistory.setGroup_id(DeviceUserHistory.GROUP_1_ID);
                    deviceUserHistory.setTitle(DeviceUserHistory.GROUP_1_1_TITLE);
                    deviceUserHistory.setMessage(DeviceUserHistory.GROUP_1_1_EVENT_ARM_ON);
                    this.getCore_().getDao().save(deviceUserHistory);
                    break;
                }
                case "arm_off": {
                    deviceUserHistory.setGroup_id(DeviceUserHistory.GROUP_1_ID);
                    deviceUserHistory.setTitle(DeviceUserHistory.GROUP_1_1_TITLE);
                    deviceUserHistory.setMessage(DeviceUserHistory.GROUP_1_1_EVENT_ARM_OFF);
                    this.getCore_().getDao().save(deviceUserHistory);
                    break;
                }
            }
        } catch (Throwable cause) {
            LOGGER.error("addDeviceUserHistory: ", cause);
        }
    }

    protected int getPayTelematicsFullPeriodDays(Payment paymentTelematics) {
        return paymentTelematics == null ? 0 :
                ((int) (ApplicationUtility.getDateInSecondsWithAddMonthCount(
                        paymentTelematics.getCreatedAt(), paymentTelematics.getPayPeriodMonths())
                        - ApplicationUtility.getDateInSeconds(paymentTelematics.getCreatedAt())) / (60 * 60 * 24));
    }

    protected int getPayTelematicsStateDays(Payment paymentTelematics, int payFullPeriodDays) {
        return paymentTelematics == null ? 0 : (payFullPeriodDays -
                ((int) (ApplicationUtility.getDateInSeconds() -
                        ApplicationUtility.getDateInSeconds(paymentTelematics.getCreatedAt())) / (60 * 60 * 24)));
    }

    protected void saveUnreadChatMessageToHazelcast(long userId, DialogMessageMobileEntity dialogMessage) {
        try {
            String dialogMessageListL2 =
                    this.getCore_().getHazelcastClient().getUnreadChatMessagesMap().get(userId);

            List<DialogMessageMobileEntity> dialogMessageList = new ArrayList<>();
            if (dialogMessageListL2 != null) {
                dialogMessageList =
                        gson.fromJson(dialogMessageListL2, new TypeToken<List<DialogMessageMobileEntity>>() {
                        }.getType());
            }

            dialogMessageList.add(dialogMessage);
            this.getCore_().getHazelcastClient().getUnreadChatMessagesMap().put(userId, gson.toJson(dialogMessageList));
        } catch (Throwable cause) {
            //LOGGER.error("Save Device to Hazelcast ERROR occurred -> " + cause.getMessage());
            System.out.println("Save DialogMessage to Hazelcast ERROR occurred -> " + cause.getMessage());
        }
    }

    public Core getCore_() {
        return core_;
    }

    public void setCore_(Core core_) {
        this.core_ = core_;
    }
}
