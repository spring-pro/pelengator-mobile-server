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
import com.pelengator.server.dao.postgresql.dto.StrfDeviceStateView;
import com.pelengator.server.dao.postgresql.entity.*;
import com.pelengator.server.exception.mobile.UnknownCommandException;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.kafka.TransportCommandObject;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.entity.response.BaseCmdResponse;
import com.pelengator.server.mobile.rest.entity.response.device.DeviceStateResponse;
import com.pelengator.server.utils.ApplicationUtility;
import com.pelengator.server.utils.DeviceLogger;
import com.pelengator.server.utils.sms.SmsSender;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;

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
                !user.getPhone().equals("79857777766")
                && !user.getPhone().equals("71111111111")
                && !user.getPhone().equals("71111111112")
                && !user.getPhone().equals("71111111113"))
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
        return sendAutofonCmdPost(core_.getGatewayCmdURL(), data);
    }

    protected BaseCmdResponse sendAutofonCmdPost(String url, TransportCommandObject data) {

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<TransportCommandObject> requestBody = new HttpEntity<>(data);

            ResponseEntity<BaseCmdResponse> result
                    = restTemplate.postForEntity(url, requestBody, BaseCmdResponse.class);

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

    protected void buttonsProcessAutoPhoneDevice(DeviceStateResponse data,
                                                 String uid,
                                                 Device device) throws Exception {

        DeviceState deviceState = this.getCore_().getDeviceState(device.getId());

        if (deviceState != null) {
            User user = this.getCore_().getUserByToken(uid);
            List<Map<String, Object>> bottomButtonsList = data.getButtons().get("bottom");
            Payment payment = this.getCore_().getPaymentTelematics(device.getId());

            int kitMaintenanceStateDays = device.getKitMaintenanceDate() == null ? 0 :
                    ((int) ((ApplicationUtility.getDateInSeconds(device.getKitMaintenanceDate())
                            - ApplicationUtility.getDateInSeconds()) / (60 * 60 * 24)));

            int payFullPeriodDays = getPayTelematicsFullPeriodDays(payment);
            int payStateDays = getPayTelematicsStateDays(payment, payFullPeriodDays);

            Map<String, Map<String, Object>> cmdIpProgress =
                    this.getCore_().getDeviceCmdInProgress().get(deviceState.getDeviceId());

            data.getButtons().get("main").forEach(item -> {
                if (user.getTypeId() != 3 &&
                        (!device.getIsActivated() ||
                                (payStateDays <= 0 &&
                                        (device.getFreeUsageFinishedAt() == null ||
                                                device.getFreeUsageFinishedAt().getTime() < ApplicationUtility.getCurrentTimeStampGMT_0())))
                ) {
                    if ((17 != Math.round((Double) item.get("id"))) && (4 != Math.round((Double) item.get("id")))) {
                        item.put("enable", 0);
                    }

                    /*
                    очередной раз переиграно - 02.07.2021 - ОТКЛ. Егор
                    if (4 == Math.round((Double) item.get("id"))) {
                        item.put("enable", 1);
                        this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "service",
                                deviceState.isValetStatus());
                    }*/
                } else if (deviceState.getStatus().equals(DeviceState.DeviceStatusEnum.DISCONNECTED.name())) {
                    if (6 != Math.round((Double) item.get("id")) &&
                            18 != Math.round((Double) item.get("id")) &&
                            2 != Math.round((Double) item.get("id")) &&
                            17 != Math.round((Double) item.get("id"))) {
                        item.put("enable", 0);
                    }
                } else {
                    item.put("enable", 1);
                    if (19 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "arm",
                                deviceState.isArmStatus());
                    } else if (1 == Math.round((Double) item.get("id"))) {
                        if (deviceState.isFortinStatus())
                            this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "engine",
                                    deviceState.isTachometerStatus());
                        else
                            item.put("enable", 0);
                    } else if (5 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "block",
                                deviceState.isAhyStatus());
                    } else if (8 == Math.round((Double) item.get("id"))) {
                        if (deviceState.isFortinStatus())
                            this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "lock",
                                    deviceState.isCentralLockStatus());
                        else
                            item.put("enable", 0);
                    } else if (4 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "service",
                                deviceState.isValetStatus());
                    } else if (12 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "alarm", false);
                    } else if (6 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "sos", true);
                    }
                }
            });

            if (deviceState.getStatus().equals(DeviceState.DeviceStatusEnum.DISCONNECTED.name()) ||
                    (payStateDays <= 0 && (device.getFreeUsageFinishedAt() == null ||
                    device.getFreeUsageFinishedAt().getTime() > ApplicationUtility.getCurrentTimeStampGMT_0()))) {

                bottomButtonsList.forEach(item -> {
                    if (208 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 208);
                        item.put("text", (Math.max(kitMaintenanceStateDays, 0)) + " дн.");
                        item.put("percent", Math.min(kitMaintenanceStateDays * 100 / 365, 100));
                    } else if (209 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 209);
                        item.put("text", (Math.max(payStateDays, 0)) + " дн.");
                        item.put("percent", payFullPeriodDays == 0 ? 0 : Math.min(payStateDays * 100 / payFullPeriodDays, 100));
                    }
                });
            } else if (deviceState.getStatus().equals(DeviceState.DeviceStatusEnum.CONNECTED.name())) {
                bottomButtonsList.forEach(item -> {
                    if (201 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 201);
                        item.put("text", String.format("%.2f", deviceState.getExternalPower() + 0.2f) + " v");
                        // в расчете учавствует коэффициент 3.5, который равен разнице между нижним и верхним порогами напряжения АКБ
                        // 13.6 - 10.1 = 3.5
                        if (deviceState.getExternalPower() + 0.2f <= 10.1f)
                            item.put("percent", 0);
                        else if (deviceState.getExternalPower() + 0.2f > 13.6f)
                            item.put("percent", 100);
                        else
                            item.put("percent", Math.round(((deviceState.getExternalPower() + 0.2f) - 10.1) * 100 / 3.5));
                    } else if (208 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 208);
                        item.put("text", (Math.max(kitMaintenanceStateDays, 0)) + " дн.");
                        item.put("percent", Math.min(kitMaintenanceStateDays * 100 / 365, 100));
                    } else if (209 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 209);
                        item.put("text", (Math.max(payStateDays, 0)) + " дн.");
                        item.put("percent", payFullPeriodDays == 0 ? 0 : Math.min(payStateDays * 100 / payFullPeriodDays, 100));
                    } else if (205 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 205);
                        item.put("percent", deviceState.getGsmQuality() * 100 / 7);
                        if (deviceState.getGsmQuality() <= 0)
                            item.put("text", "нет связи");
                        else if (deviceState.getGsmQuality() > 0 && deviceState.getGsmQuality() < 2)
                            item.put("text", "плохо");
                        else if (deviceState.getGsmQuality() >= 2 && deviceState.getGsmQuality() < 4)
                            item.put("text", "умеренно");
                        else if (deviceState.getGsmQuality() >= 4 && deviceState.getGsmQuality() < 6)
                            item.put("text", "хорошо");
                        else if (deviceState.getGsmQuality() >= 6)
                            item.put("text", "отлично");
                    } else if (206 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 206);
                        item.put("percent", deviceState.getGpsQuality() * 100 / 18);
                        if (deviceState.getGpsQuality() <= 0)
                            item.put("text", "нет связи");
                        else if (deviceState.getGpsQuality() > 0 && deviceState.getGpsQuality() < 5)
                            item.put("text", "плохо");
                        else if (deviceState.getGpsQuality() >= 5 && deviceState.getGpsQuality() < 10)
                            item.put("text", "умеренно");
                        else if (deviceState.getGpsQuality() >= 10 && deviceState.getGpsQuality() < 14)
                            item.put("text", "хорошо");
                        else if (deviceState.getGpsQuality() >= 14)
                            item.put("text", "отлично");
                    } else if (202 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 202);
                        item.put("text", String.format("%.2f", (deviceState.getBatteryPower())) + " v");
                        item.put("percent", Math.round((deviceState.getBatteryPower()) * 100 / 4.24));
                    }
                });

                if (payStateDays <= 0 && (device.getFreeUsageFinishedAt() == null ||
                        device.getFreeUsageFinishedAt().getTime() > ApplicationUtility.getCurrentTimeStampGMT_0())) {

                    int freeUsageDays = 0;

                    if (device.getFreeUsageFinishedAt() != null)
                        freeUsageDays = (int) ((device.getFreeUsageFinishedAt().getTime() - ApplicationUtility.getCurrentTimeStampGMT_0()) / (1000 * 60 * 60 * 24));

                    Map<String, Object> item = new HashMap<>(3);
                    item.put("icon_id", 210);

                    if (freeUsageDays == 1)
                        item.put("text", "День");
                    else if (freeUsageDays > 1 && freeUsageDays < 5)
                        item.put("text", "Дня");
                    else
                        item.put("text", "Дней");

                    item.put("percent", freeUsageDays * 100 / 10);
                    item.put("enable", 1);
                    bottomButtonsList.add(item);
                }

                data.getButtons().put("bottom", bottomButtonsList);
            }
        }
    }

    protected void buttonsProcessStartFoneDevice(DeviceStateResponse data,
                                                 String uid,
                                                 Device device) throws Exception {

        StrfDeviceStateView deviceState =
                getCore_().getStrfDao().getDeviceState(device.getId());

        if (deviceState != null) {
            User user = this.getCore_().getUserByToken(uid);
            List<Map<String, Object>> bottomButtonsList = data.getButtons().get("bottom");
            Payment payment = this.getCore_().getPaymentTelematics(device.getId());

            int kitMaintenanceStateDays = device.getKitMaintenanceDate() == null ? 0 :
                    ((int) ((ApplicationUtility.getDateInSeconds(device.getKitMaintenanceDate())
                            - ApplicationUtility.getDateInSeconds()) / (60 * 60 * 24)));

            int payFullPeriodDays = getPayTelematicsFullPeriodDays(payment);
            int payStateDays = getPayTelematicsStateDays(payment, payFullPeriodDays);

            Map<String, Map<String, Object>> cmdIpProgress =
                    this.getCore_().getDeviceCmdInProgress().get(device.getId());

            data.getButtons().get("main").forEach(item -> {

                if (!deviceState.getConnected()) {
                    if (6 != Math.round((Double) item.get("id")) &&
                            18 != Math.round((Double) item.get("id")) &&
                            2 != Math.round((Double) item.get("id")) &&
                            17 != Math.round((Double) item.get("id"))) {
                        item.put("enable", 0);
                    }
                } else {
                    if (user.getTypeId() != 3 &&
                            (!device.getIsActivated() ||
                                    (payStateDays <= 0 &&
                                            (device.getFreeUsageFinishedAt() == null ||
                                                    device.getFreeUsageFinishedAt().getTime() < ApplicationUtility.getCurrentTimeStampGMT_0())))
                    ) {
                        if ((17 != Math.round((Double) item.get("id"))) && (4 != Math.round((Double) item.get("id")))) {
                            item.put("enable", 0);
                        }

                        /*
                        очередной раз переиграно - 02.07.2021 - ОТКЛ. Егор
                        if (4 == Math.round((Double) item.get("id"))) {
                            item.put("enable", 1);
                            this.getCore_().getCmdBtnState(cmdIpProgress, item, "service",
                                    deviceState.getServiceState());
                        }*/
                    } else {
                        if (19 == Math.round((Double) item.get("id"))) {
                            if (deviceState.getArmDisarmControl()) {
                                item.put("enable", 1);
                                this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "arm",
                                        deviceState.getArmState());
                            }
                        } else if (1 == Math.round((Double) item.get("id"))) {
                            if (deviceState.getAutostartState()) {
                                item.put("enable", 1);
                                this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "engine",
                                        deviceState.getEngineState());
                            }
                        } else if (5 == Math.round((Double) item.get("id"))) {
                            if (deviceState.getBlockControl()) {
                                item.put("enable", 1);
                                this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "block",
                                        deviceState.getBlockState());
                            }
                        } else if (8 == Math.round((Double) item.get("id"))) {
                        /*this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "lock",
                                deviceState.isCentralLockStatus());*/
                        } else if (4 == Math.round((Double) item.get("id"))) {
                            item.put("enable", 1);
                            this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "service",
                                    deviceState.getServiceState());
                        } else if (12 == Math.round((Double) item.get("id"))) {
                            item.put("enable", 1);
                            this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "alarm", false);
                        } else if (6 == Math.round((Double) item.get("id"))) {
                            item.put("enable", 1);
                            this.getCore_().getCmdBtnStateV1(cmdIpProgress, item, "sos", true);
                        }
                    }
                }
            });

            if (deviceState.getConnected()) {
                bottomButtonsList.forEach(item -> {
                    if (201 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 201);
                        item.put("text", String.format("%.2f", deviceState.getVoltage()) + " v");
                        // в расчете учавствует коэффициент 3.5, который равен разнице между нижним и верхним порогами напряжения АКБ
                        // 13.6 - 10.1 = 3.5
                        if (deviceState.getVoltage() <= 10.1f)
                            item.put("percent", 0);
                        else if (deviceState.getVoltage() > 13.6f)
                            item.put("percent", 100);
                        else
                            item.put("percent", Math.round(((deviceState.getVoltage()) - 10.1) * 100 / 3.5));
                    } else if (208 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 208);
                        item.put("text", (Math.max(kitMaintenanceStateDays, 0)) + " дн.");
                        item.put("percent", Math.min(kitMaintenanceStateDays * 100 / 365, 100));
                    } else if (209 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 209);
                        item.put("text", (Math.max(payStateDays, 0)) + " дн.");
                        item.put("percent", payFullPeriodDays == 0 ? 0 : Math.min(payStateDays * 100 / payFullPeriodDays, 100));
                    } else if (205 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 205);

                    } else if (206 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 206);
                        item.put("percent", deviceState.getGpsQuality() * 100 / 18);
                        if (deviceState.getGpsQuality() <= 0)
                            item.put("text", "нет связи");
                        else if (deviceState.getGpsQuality() > 0 && deviceState.getGpsQuality() < 5)
                            item.put("text", "плохо");
                        else if (deviceState.getGpsQuality() >= 5 && deviceState.getGpsQuality() < 10)
                            item.put("text", "умеренно");
                        else if (deviceState.getGpsQuality() >= 10 && deviceState.getGpsQuality() < 14)
                            item.put("text", "хорошо");
                        else if (deviceState.getGpsQuality() >= 14)
                            item.put("text", "отлично");
                    } else if (202 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 202);
                        item.put("text", "0 v");
                        item.put("percent", 0);
                    }
                });

                if (payStateDays <= 0 && (device.getFreeUsageFinishedAt() == null ||
                        device.getFreeUsageFinishedAt().getTime() > ApplicationUtility.getCurrentTimeStampGMT_0())) {

                    int freeUsageDays = 0;

                    if (device.getFreeUsageFinishedAt() != null)
                        freeUsageDays = (int) ((device.getFreeUsageFinishedAt().getTime() - ApplicationUtility.getCurrentTimeStampGMT_0()) / (1000 * 60 * 60 * 24));

                    Map<String, Object> item = new HashMap<>(3);
                    item.put("icon_id", 210);

                    if (freeUsageDays == 1)
                        item.put("text", "День");
                    else if (freeUsageDays > 1 && freeUsageDays < 5)
                        item.put("text", "Дня");
                    else
                        item.put("text", "Дней");

                    item.put("percent", freeUsageDays * 100 / 10);
                    item.put("enable", 1);
                    bottomButtonsList.add(item);
                }

                data.getButtons().put("bottom", bottomButtonsList);
            } else {
                bottomButtonsList.forEach(item -> {
                    if (208 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 208);
                        item.put("text", (Math.max(kitMaintenanceStateDays, 0)) + " дн.");
                        item.put("percent", Math.min(kitMaintenanceStateDays * 100 / 365, 100));
                    } else if (209 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 209);
                        item.put("text", (Math.max(payStateDays, 0)) + " дн.");
                        item.put("percent", payFullPeriodDays == 0 ? 0 : Math.min(payStateDays * 100 / payFullPeriodDays, 100));
                    }
                });
            }
        }
    }

    protected ResponseEntity sendCmdAutoPhoneDevice(Device device, String cmd, long uid) throws Exception {
        boolean isSendCmdToGateway = true;
        int sentMessagesCount = 0;

        DeviceState deviceState =
                this.getCore_().getDeviceState(this.getCore_().getUserCurrentDevice(uid));

        DeviceLog deviceLog = new DeviceLog();
        try {
            deviceLog.setDeviceId(device.getId());
            deviceLog.setAdminId(null);
            deviceLog.setUserId(uid);
            deviceLog.setSenderType(DeviceLog.CommandSenderTypeEnum.USER.name());
            deviceLog.setLogType(DeviceLogger.LOG_TYPE_OUTPUT_EVENT);
            deviceLog.setEventType(0);
            deviceLog.setMessage(cmd);
            deviceLog.setSent(false);
            deviceLog.setDescription("");
            deviceLog.setErrMsg("");
            deviceLog.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
            deviceLog.setUpdatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
            this.getCore_().getDao().save(deviceLog);
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/cmd: ", cause);
        }

        BaseCmdResponse response = new BaseCmdResponse();

        switch (cmd) {
            case "engine_on": {
                ByteBuf resultCmd = Unpooled.buffer().writeBytes(AutofonCommands.AUTOFON_CMD_ENGINE_START.duplicate());

                UserSettings userSettings = this.getCore_().getSettings(uid);
                if (userSettings.getAutoStartRuntime() != null && userSettings.getAutoStartRuntime() > 0) {
                    resultCmd.writeByte((byte) (userSettings.getAutoStartRuntime() & 255));
                } else {
                    resultCmd.writeByte((byte) (10 & 255));
                }

                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        resultCmd.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "engine", false);
                break;
            }
            case "engine_off": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_ENGINE_STOP.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "engine", true);
                break;
            }
            case "arm_on": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_ARM_ENABLE.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "arm", false);
                break;
            }
            case "arm_off": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_ARM_DISABLE.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "arm", true);
                break;
            }
            case "block_on": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_ENGINE_LOCK.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "block", false);
                break;
            }
            case "block_off": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_ENGINE_UNLOCK.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "block", true);
                break;
            }
            case "locking_on": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_LOCK.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "lock", false);
                break;
            }
            case "locking_off": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_UNLOCK.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "lock", true);
                break;
            }
            case "alarm_on": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_SEARCH_CAR.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "alarm", false);
                break;
            }
            case "service_on": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_SERVICE_ENABLE.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "service", false);
                break;
            }
            case "service_off": {
                response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                        AutofonCommands.AUTOFON_CMD_SERVICE_DISABLE.toString(StandardCharsets.ISO_8859_1)));
                getCore_().addDeviceCmdInProgress(device.getId(), "service", true);
                break;
            }
            case "sos_on": {
                isSendCmdToGateway = false;
                UserSettings userSettings = this.getCore_().getSettings(uid);
                if (!StringUtils.isBlank(userSettings.getSosPhones())) {
                    User user = this.getCore_().getDao().find(User.class, uid);

                    String[] sosPhones = userSettings.getSosPhones().split(",", -1);
                    for (String sosPhone : sosPhones) {
                        if (!StringUtils.isBlank(sosPhone) && sosPhone.length() > 7) {
                            try {
                                if (SmsSender.send(sosPhone, "Сообщение об экстренной " +
                                        "ситуации от пользователя тел. " + user.getPhone() + " , позиция: " +
                                        "https://maps.yandex.ru?text=" + deviceState.getLatitude() + "," + deviceState.getLongitude()))
                                    sentMessagesCount++;

                                getCore_().addDeviceCmdInProgress(device.getId(), "sos", false);
                            } catch (Throwable cause) {
                                getCore_().addDeviceCmdInProgress(device.getId(), "sos", true);
                                LOGGER.error("SEND SMS FATAL error -> " + cause.getMessage());
                            }
                        }
                    }
                }
                break;
            }
            default: {
                throw new UnknownCommandException(HttpStatus.OK.value());
            }
        }

        if (isSendCmdToGateway) {
            if (response.getCode() == HttpStatus.OK.value()) {
                Map<String, String> data = new HashMap<>(1);
                data.put("answer", "Команда отправлена!");

                addDeviceUserHistory(cmd, device.getId());

                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.OK.value(), "", data));
            } else {
                throw new Exception(response.getMessage());
            }
        } else {
            Map<String, String> data = new HashMap<>(1);
            data.put("answer", "Команда отправлена!");

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(),
                            "Успешно отправлено сообщений: " + sentMessagesCount, data));
        }
    }

    protected ResponseEntity sendCmdStartFoneDevice(Device device, String cmd, long uid) throws Exception {
        //TODO sign !!!
        String url = "https://test-pelengator.com:27443/startfone-device-server/cmd/sign";
        boolean isSendCmd = true;
        int sentMessagesCount = 0;

        DeviceState deviceState =
                this.getCore_().getDeviceState(this.getCore_().getUserCurrentDevice(uid));

        BaseCmdResponse response = new BaseCmdResponse();

        switch (cmd) {
            case "engine_on": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "engine", false);
                break;
            }
            case "engine_off": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "engine", true);
                break;
            }
            case "arm_on": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "arm", false);
                break;
            }
            case "arm_off": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "arm", true);
                break;
            }
            case "block_on": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "block", false);
                break;
            }
            case "block_off": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "block", true);
                break;
            }
            case "alarm_on": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "alarm", false);
                break;
            }
            case "service_on": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "service", false);
                break;
            }
            case "service_off": {
                response = sendAutofonCmdPost(url, new TransportCommandObject(device.getImei(), null, cmd));
                getCore_().addDeviceCmdInProgress(device.getId(), "service", true);
                break;
            }
            case "sos_on": {
                break;
            }
            default: {
                throw new UnknownCommandException(HttpStatus.OK.value());
            }
        }

        if (isSendCmd) {
            if (response.getCode() == HttpStatus.OK.value()) {
                Map<String, String> data = new HashMap<>(1);
                data.put("answer", "Команда отправлена!");

                addDeviceUserHistory(cmd, device.getId());

                DeviceLog deviceLog = new DeviceLog();
                try {
                    deviceLog.setDeviceId(device.getId());
                    deviceLog.setAdminId(null);
                    deviceLog.setUserId(uid);
                    deviceLog.setSenderType(DeviceLog.CommandSenderTypeEnum.USER.name());
                    deviceLog.setLogType(DeviceLogger.LOG_TYPE_OUTPUT_EVENT);
                    deviceLog.setEventType(0);
                    deviceLog.setMessage(cmd);
                    deviceLog.setSent(true);
                    deviceLog.setDescription("");
                    deviceLog.setErrMsg("");
                    deviceLog.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                    deviceLog.setUpdatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                    this.getCore_().getDao().save(deviceLog);
                } catch (Throwable cause) {
                    LOGGER.error("REQUEST error -> /device/cmd: ", cause);
                }

                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.OK.value(), "", data));
            } else {
                throw new Exception(response.getMessage());
            }
        } else {
            Map<String, String> data = new HashMap<>(1);
            data.put("answer", "Команда отправлена!");

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(),
                            "Успешно отправлено сообщений: " + sentMessagesCount, data));
        }
    }

    public Core getCore_() {
        return core_;
    }

    public void setCore_(Core core_) {
        this.core_ = core_;
    }
}
