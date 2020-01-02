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
import com.pelengator.server.autofon.AutofonCommands;
import com.pelengator.server.dao.postgresql.DevicePositionDao;
import com.pelengator.server.dao.postgresql.dto.DialogMessageMobileEntity;
import com.pelengator.server.dao.postgresql.entity.*;
import com.pelengator.server.exception.mobile.*;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.kafka.TransportCommandObject;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.ErrorResponse;
import com.pelengator.server.mobile.rest.entity.BaseEntity;
import com.pelengator.server.mobile.rest.entity.request.device.*;
import com.pelengator.server.mobile.rest.entity.response.BaseCmdResponse;
import com.pelengator.server.mobile.rest.entity.response.device.DeviceAddResponse;
import com.pelengator.server.mobile.rest.entity.response.device.DevicePositionResponse;
import com.pelengator.server.mobile.rest.entity.response.device.DeviceSettingsResponse;
import com.pelengator.server.mobile.rest.entity.response.device.DeviceStateResponse;
import com.pelengator.server.utils.ApplicationUtility;
import com.pelengator.server.utils.DeviceLogger;
import com.pelengator.server.utils.sms.SmsSender;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/device")
public class DeviceController extends BaseController {

    private static final Logger LOGGER = Core.getLogger(DeviceController.class.getSimpleName());

    @RequestMapping(value = "/get/available_payments/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getAvailablePayments(@PathVariable("token") String token,
                                               @PathVariable("uid") long uid,
                                               @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            Device device;

            if (!StringUtils.isBlank(requestBody)) {
                DeviceGetAvailablePaymentsRequest request =
                        BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                                DeviceGetAvailablePaymentsRequest.class);

                if (request == null)
                    throw new UnknownException(HttpStatus.OK.value());

                if (request.getDeviceId() != null)
                    device = this.getCore_().getDao().find(Device.class, request.getDeviceId());
                else
                    device = this.getCore_().getDao().find(Device.class,
                            this.getCore_().getUserCurrentDevice(uid));
            } else {
                device = this.getCore_().getDao().find(Device.class,
                        this.getCore_().getUserCurrentDevice(uid));
            }

            Payment paymentActivation = this.getCore_().getPaymentDao().getPayedPayment(
                    device.getId(), Payment.PAY_TYPE_ACTIVATION);

            Payment paymentTelematics = this.getCore_().getPaymentDao().getPayedPayment(
                    device.getId(), Payment.PAY_TYPE_TELEMATICS);

            Map<String, Object> activation = new HashMap<>();
            activation.put("text", device.getIsActivated() ? "" : "20000 р.");
            activation.put("color", device.getIsActivated() ? PAYMENT_ITEM_STATE_INACTIVE : PAYMENT_ITEM_STATE_NEED_PAY);

            Map<String, Object> telematics = new HashMap<>();
            if (!device.getIsActivated()) {
                telematics.put("text", paymentTelematics == null ? "не оплачено" : "оплачено");
                telematics.put("color", PAYMENT_ITEM_STATE_INACTIVE);
            } else {
                if (paymentTelematics == null) {
                    telematics.put("text", "3900 р.");
                    telematics.put("color", PAYMENT_ITEM_STATE_NEED_PAY);
                } else {
                    int payFullPeriodDays = getPayTelematicsFullPeriodDays(paymentTelematics);
                    int payStateDays = getPayTelematicsStateDays(paymentTelematics, payFullPeriodDays);
                    telematics.put("text", payStateDays + "дн.");
                    telematics.put("color", PAYMENT_ITEM_STATE_ACTIVE);
                }
            }

            /*Map<String, Object> installment = new HashMap<>();
            installment.put("text", "text");
            installment.put("color", 2);*/


            Map<String, Map<String, Object>> data = new HashMap<>();
            /*data.put("activation", activation);*/
            data.put("telematics", telematics);
            /*data.put("installment", installment);*/

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /device/get/available_payments: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/available_payments: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/add/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity addDevice(@PathVariable("token") String token,
                                    @PathVariable("uid") long uid,
                                    @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceAddRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceAddRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            DeviceAddResponse data = new DeviceAddResponse();
            Device device = this.getCore_().getDao().find(Device.class, "imei", request.getDeviceImei());

            if (device == null)
                throw new IncorrectIMEIException(HttpStatus.OK.value());

            if (device.getKitMaintenanceDate() == null)
                device.setKitMaintenanceDate(new Date(ApplicationUtility.getDateInSecondsWithAddYearsCount(1) * 1000));

            UserDevice userDevice = this.getCore_().getUserDeviceDao().getUserDevice(uid, device.getId(), false);
            if (userDevice == null) {
                userDevice = new UserDevice();
                userDevice.setUserId(uid);
                userDevice.setDeviceId(device.getId());
                userDevice.setCarBrand(request.getCarBrand());
                userDevice.setCarModel(request.getCarModel());
                userDevice.setCarNumber(request.getCarNumber());
                userDevice.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                userDevice.setConfirmed(false);
            }

            try {
                this.getCore_().getDao().save(userDevice);
            } catch (ConstraintViolationException cve) {
                throw new DataAlreadyExistsException(HttpStatus.OK.value(),
                        "Устройство с заданным IMEI уже привязано к текущему пользователю!");
            }

            data.setConfirmType("pass");
            data.setDeviceId(userDevice.getDeviceId());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /device/set: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/state: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/add_confirm/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity addDeviceConfirm(@PathVariable("token") String token,
                                           @PathVariable("uid") long uid,
                                           @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceAddConfirmRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceAddConfirmRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            List<UserDevice> userDeviceList = this.getCore_().getUserDeviceDao().getUserDeviceList(request.getDeviceId(), 1, true);
            UserDevice userDevice = this.getCore_().getUserDeviceDao().getUserDevice(uid, request.getDeviceId(), false);

            if (userDevice != null) {
                Device device = this.getCore_().getDao().find(Device.class, userDevice.getDeviceId());

                if (device != null) {
                    if (device.getPassword().equals(request.getConfirm())) {
                        userDevice.setConfirmed(true);

                        if (userDeviceList == null || userDeviceList.size() == 0) {
                            device.setFreeUsageFinishedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0() + (10 * 24 * 60 * 60 * 1000)));
                            this.getCore_().getDao().save(device);
                        }

                        this.getCore_().getDao().save(userDevice);
                    } else
                        throw new IncorrectDevicePasswordException(HttpStatus.OK.value());
                } else
                    throw new UnknownDeviceException(HttpStatus.OK.value());
            } else
                LOGGER.error("userDevice is not found!!! -> " + uid + " - " + request.getDeviceId());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /device/set: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/state: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/edit/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity editDevice(@PathVariable("token") String token,
                                     @PathVariable("uid") long uid,
                                     @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceEditRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceEditRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            UserDevice userDevice = this.getCore_().getUserDeviceDao().getUserDevice(uid, request.getDeviceImei());

            if (userDevice != null) {
                userDevice.setCarBrand(request.getCarBrand());
                userDevice.setCarModel(request.getCarModel());
                userDevice.setCarNumber(request.getCarNumber());
                this.getCore_().getDao().save(userDevice);
            } else throw new IncorrectIMEIException(HttpStatus.OK.value());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /device/set: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/state: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/delete/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity deleteDevice(@PathVariable("token") String token,
                                       @PathVariable("uid") long uid,
                                       @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceDeleteRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceDeleteRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            UserDevice userDevice = this.getCore_().getUserDeviceDao().getUserDevice(uid, request.getDeviceImei());

            if (userDevice != null) {
                this.getCore_().getDao().delete(userDevice);
            } else throw new IncorrectIMEIException(HttpStatus.OK.value());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /device/set: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/state: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/set/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity deviceSet(
            @PathVariable("token") String token,
            @PathVariable("uid") long uid,
            @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceSetRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody), DeviceSetRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            Device device = this.getCore_().setUserCurrentDevice(uid, Long.parseLong(request.getImei()));

            DeviceState deviceState =
                    this.getCore_().getDeviceState(this.getCore_().getUserCurrentDevice(uid));

            if (deviceState != null &&
                    deviceState.getStatus().equals(DeviceState.DeviceStatusEnum.DISCONNECTED.name())) {

                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.OK.value(), "Нет связи с автомобилем!", null));
            } else {
                sendAutoStatusCmd(device, deviceState);
                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.OK.value(), "", null));
            }
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /device/set: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/set: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/settings/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDeviceSettings(@PathVariable("token") String token,
                                            @PathVariable("uid") long uid) {

        try {
            Gson gson = new Gson();
            DeviceSettingsResponse data = gson.fromJson("{\n" +
                    "    \"buttons\": {\n" +
                    "        \"bottom\": [\n" +
                    "            {\n" +
                    "                \"id\": 201,\n" +
                    "                \"v\": 1\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"id\": 208,\n" +
                    "                \"v\": 1\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"id\": 209,\n" +
                    "                \"v\": 1\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"id\": 205,\n" +
                    "                \"v\": 1\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"id\": 206,\n" +
                    "                \"v\": 1\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"id\": 202,\n" +
                    "                \"v\": 1\n" +
                    "            }\n" +
                    "        ],\n" +
                    "        \"main\": [\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 19,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": true,\n" +
                    "                \"button_settings\": [\n" +
                    "                    {\n" +
                    "                        \"status\": 0,\n" +
                    "                        \"cmd\": \"/device/cmd/arm_on\",\n" +
                    "                        \"name\": \"Включить охрану\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 1,\n" +
                    "                        \"cmd\": \"\",\n" +
                    "                        \"name\": \"Ожидание ответа от сервера\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 2,\n" +
                    "                        \"cmd\": \"/device/cmd/arm_off\",\n" +
                    "                        \"name\": \"Выключить охрану\"\n" +
                    "                    }\n" +
                    "                ]\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 6,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": true,\n" +
                    "                \"button_settings\": [\n" +
                    "                    {\n" +
                    "                        \"status\": 0,\n" +
                    "                        \"cmd\": \"/device/cmd/sos_on\",\n" +
                    "                        \"name\": \"Включить sos\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 1,\n" +
                    "                        \"cmd\": \"\",\n" +
                    "                        \"name\": \"Ожидание ответа от сервера\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 2,\n" +
                    "                        \"cmd\": \"/device/cmd/sos_off\",\n" +
                    "                        \"name\": \"Выключить sos\"\n" +
                    "                    }\n" +
                    "                ]\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 0,\n" +
                    "                \"id\": 1,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": true,\n" +
                    "                \"button_settings\": [\n" +
                    "                    {\n" +
                    "                        \"status\": 0,\n" +
                    "                        \"cmd\": \"/device/cmd/engine_on\",\n" +
                    "                        \"name\": \"Включить двигатель\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 1,\n" +
                    "                        \"cmd\": \"\",\n" +
                    "                        \"name\": \"Ожидание ответа от сервера\",\n" +
                    "                        \"image\": \"1_2\",\n" +
                    "                        \"sound\": \"\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 2,\n" +
                    "                        \"cmd\": \"/device/cmd/engine_off\",\n" +
                    "                        \"name\": \"Выключить двигатель\"\n" +
                    "                    }\n" +
                    "                ]\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 8,\n" +
                    "                \"need_pin\": true,\n" +
                    "                \"long_press\": true,\n" +
                    "                \"button_settings\": [\n" +
                    "                    {\n" +
                    "                        \"status\": 0,\n" +
                    "                        \"cmd\": \"/device/cmd/locking_on\",\n" +
                    "                        \"name\": \"Закрыть ЦЗу\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 1,\n" +
                    "                        \"cmd\": \"\",\n" +
                    "                        \"name\": \"Ожидание ответа от сервера\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 2,\n" +
                    "                        \"cmd\": \"/device/cmd/locking_off\",\n" +
                    "                        \"name\": \"Открыть ЦЗ\"\n" +
                    "                    }\n" +
                    "                ]\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 3,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": false,\n" +
                    "                \"name\": \"Местоположение\",\n" +
                    "                \"button_action\": \"show_position\",\n" +
                    "                \"image\": \"3_1\"\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 12,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": true,\n" +
                    "                \"button_settings\": [\n" +
                    "                    {\n" +
                    "                        \"status\": 0,\n" +
                    "                        \"cmd\": \"/device/cmd/alarm_on\",\n" +
                    "                        \"name\": \"Включить тревогу\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 1,\n" +
                    "                        \"cmd\": \"\",\n" +
                    "                        \"name\": \"Ожидание ответа от сервера\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 2,\n" +
                    "                        \"cmd\": \"/device/cmd/alarm_off\",\n" +
                    "                        \"name\": \"Выключить тревогу\"\n" +
                    "                    }\n" +
                    "                ]\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 5,\n" +
                    "                \"need_pin\": true,\n" +
                    "                \"long_press\": true,\n" +
                    "                \"button_settings\": [\n" +
                    "                    {\n" +
                    "                        \"status\": 0,\n" +
                    "                        \"cmd\": \"/device/cmd/block_on\",\n" +
                    "                        \"name\": \"Включить блокировку\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 1,\n" +
                    "                        \"cmd\": \"\",\n" +
                    "                        \"name\": \"Ожидание ответа от сервера\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 2,\n" +
                    "                        \"cmd\": \"/device/cmd/block_off\",\n" +
                    "                        \"name\": \"Выключить блокировку\"\n" +
                    "                    }\n" +
                    "                ]\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 18,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": false,\n" +
                    "                \"name\": \"История\",\n" +
                    "                \"button_action\": \"show_history\",\n" +
                    "                \"image\": \"18_1\"\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 4,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": true,\n" +
                    "                \"button_settings\": [\n" +
                    "                    {\n" +
                    "                        \"status\": 0,\n" +
                    "                        \"cmd\": \"/device/cmd/service_on\",\n" +
                    "                        \"name\": \"Включить сервисный режим\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 1,\n" +
                    "                        \"cmd\": \"\",\n" +
                    "                        \"name\": \"Ожидание ответа от сервера\"\n" +
                    "                    },\n" +
                    "                    {\n" +
                    "                        \"status\": 2,\n" +
                    "                        \"cmd\": \"/device/cmd/service_off\",\n" +
                    "                        \"name\": \"Выключить сервисный режим\"\n" +
                    "                    }\n" +
                    "                ]\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 17,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": false,\n" +
                    "                \"name\": \"Инструкция\",\n" +
                    "                \"button_action\": \"show_manual\",\n" +
                    "                \"image\": \"17_1\"\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 13,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": false,\n" +
                    "                \"name\": \"Микрофон\",\n" +
                    "                \"button_action\": \"show_microphone\",\n" +
                    "                \"image\": \"13_1\"\n" +
                    "            },\n" +
                    "            {\n" +
                    "                \"enable\": 1,\n" +
                    "                \"id\": 2,\n" +
                    "                \"need_pin\": false,\n" +
                    "                \"long_press\": false,\n" +
                    "                \"name\": \"Трекинг\",\n" +
                    "                \"button_action\": \"show_tracking\",\n" +
                    "                \"image\": \"2_1\"\n" +
                    "            }\n" +
                    "        ]\n" +
                    "    },\n" +
                    "    \"balance_in_menu\": true,\n" +
                    "    \"autostart_runtime\": 10,\n" +
                    "    \"all_buttons\": [\n" +
                    "        {\n" +
                    "            \"id\": 19,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 6,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 1,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 8,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 3,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 12,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 5,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 18,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 4,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 17,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 13,\n" +
                    "            \"v\": 1\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"id\": 2,\n" +
                    "            \"v\": 1\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}", DeviceSettingsResponse.class);

            UserSettings userSettings = this.getCore_().getSettings(uid);
            if (userSettings.getAutoStartRuntime() != null && userSettings.getAutoStartRuntime() > 0)
                data.setAutostartRuntime(userSettings.getAutoStartRuntime());

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/settings: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    /**
     * References:
     * BATTERY(201),
     * BUILT_IN_BATTERY(202),
     * INSIDE_TEMPERATURE(204),
     * PAYMENT(209),
     * SERVICE(208),
     * SIGNAL_GPS(206),
     * SIGNAL_GSM(205),
     * GASOLINE(207),
     * ODOMETER(211),
     * FREE_PUSH(210),
     * LABEL(212);
     *
     * @param token - token from thw header
     * @param uid   - user id
     * @return - result json
     */

    @RequestMapping(value = "/get/state/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDeviceState(@PathVariable("token") String token,
                                         @PathVariable("uid") long uid) {

        try {
            String stateTemp = "{\n" +
                    "  \"data_ts\": 1571137306,\n" +
                    "  \"buttons\": {\n" +
                    "    \"bottom\": [\n" +
                    "      {\n" +
                    "        \"icon_id\": 201,\n" +
                    "        \"text\": \"0,00 v\",\n" +
                    "        \"percent\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"icon_id\": 208,\n" +
                    "        \"text\": \"0 дн.\",\n" +
                    "        \"percent\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"icon_id\": 209,\n" +
                    "        \"text\": \"0 дн.\",\n" +
                    "        \"percent\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"icon_id\": 205,\n" +
                    "        \"text\": \"умеренно\",\n" +
                    "        \"percent\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"icon_id\": 206,\n" +
                    "        \"text\": \"без связи\",\n" +
                    "        \"percent\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"icon_id\": 202,\n" +
                    "        \"text\": \"0,00 v\",\n" +
                    "        \"percent\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"main\": [\n" +
                    "      {\n" +
                    "        \"id\": 19,\n" +
                    "        \"state_id\": 0,\n" +
                    "        \"enable\": 0\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 6,\n" +
                    "        \"state_id\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 1,\n" +
                    "        \"state_id\": 0,\n" +
                    "        \"enable\": 0\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 8,\n" +
                    "        \"enable\": 0\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 3,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 12,\n" +
                    "        \"enable\": 0\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 5,\n" +
                    "        \"state_id\": 0,\n" +
                    "        \"enable\": 0\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 18,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 4,\n" +
                    "        \"state_id\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 17,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 13,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 2,\n" +
                    "        \"enable\": 1\n" +
                    "      }\n" +
                    "    ]\n" +
                    "  },\n" +
                    "  \"test_status\": {\n" +
                    "    \"stat\": 0\n" +
                    "  },\n" +
                    "  \"all_statuses\": {\n" +
                    "    \"service\": false\n" +
                    "  },\n" +
                    "  \"messages\": []\n" +
                    "}";

            DeviceStateResponse data = gson.fromJson(stateTemp, DeviceStateResponse.class);
            List<Map<String, Object>> bottomButtonsList = data.getButtons().get("bottom");

            Device currentDevice = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));
            if (currentDevice == null)
                // If current device is not set
                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.REQUEST_TIMEOUT.value(), "Ведутся технические работы, Подождите, пожалуйста ...", data));

            DeviceState deviceState =
                    this.getCore_().getDeviceState(currentDevice.getId());

            if (deviceState != null) {
                User user = this.getCore_().getUserByToken(token);
                Device device = this.getCore_().getDevice(deviceState.getDeviceId());
                Payment payment = this.getCore_().getPaymentTelematics(deviceState.getDeviceId());

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
                            this.getCore_().getCmdBtnState(cmdIpProgress, item, "arm",
                                    deviceState.isArmStatus());
                        } else if (1 == Math.round((Double) item.get("id"))) {
                            if (deviceState.isFortinStatus())
                                this.getCore_().getCmdBtnState(cmdIpProgress, item, "engine",
                                        deviceState.isTachometerStatus());
                            else
                                item.put("enable", 0);
                        } else if (5 == Math.round((Double) item.get("id"))) {
                            this.getCore_().getCmdBtnState(cmdIpProgress, item, "block",
                                    deviceState.isAhyStatus());
                        } else if (8 == Math.round((Double) item.get("id"))) {
                            this.getCore_().getCmdBtnState(cmdIpProgress, item, "lock",
                                    deviceState.isCentralLockStatus());
                        } else if (4 == Math.round((Double) item.get("id"))) {
                            this.getCore_().getCmdBtnState(cmdIpProgress, item, "service",
                                    deviceState.isValetStatus());
                        } else if (12 == Math.round((Double) item.get("id"))) {
                            this.getCore_().getCmdBtnState(cmdIpProgress, item, "alarm", false);
                        } else if (6 == Math.round((Double) item.get("id"))) {
                            this.getCore_().getCmdBtnState(cmdIpProgress, item, "sos", true);
                        }
                    }
                });

                if (deviceState.getStatus().equals(DeviceState.DeviceStatusEnum.CONNECTED.name())) {
                    bottomButtonsList.forEach(item -> {
                        if (201 == Math.round((Double) item.get("icon_id"))) {
                            item.put("icon_id", 201);
                            item.put("text", String.format("%.2f", (deviceState.getExternalPower())) + " v");
                            item.put("percent", Math.round((deviceState.getExternalPower()) * 100 / 15));
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
                            item.put("text", deviceState.getGsmQuality() + " шт.");
                            item.put("percent", deviceState.getGsmQuality() * 100 / 7);
                        } else if (206 == Math.round((Double) item.get("icon_id"))) {
                            item.put("icon_id", 206);
                            item.put("text", deviceState.getGpsQuality() + " шт.");
                            item.put("percent", deviceState.getGpsQuality() * 100 / 17);
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
                } else {
                    bottomButtonsList.forEach(item -> {
                        if (208 == Math.round((Double) item.get("icon_id"))) {
                            item.put("icon_id", 208);
                            item.put("text", (Math.max(kitMaintenanceStateDays, 0)) + " дн.");
                            item.put("percent", Math.min(kitMaintenanceStateDays * 100 / 365, 100));
                        }
                    });
                }
            }

            List<DialogMessageMobileEntity> messages = this.getCore_().getUnreadChatMessagesFromCacheL2(uid);
            if (messages != null)
                data.getMessages().addAll(messages);

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/state: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/cmd/{cmd}/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity sendCmd(@PathVariable("cmd") String cmd,
                                  @PathVariable("token") String token,
                                  @PathVariable("uid") long uid) {

        try {
            boolean isSendCmdToGateway = true;
            int sentMessagesCount = 0;

            Device device = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));

            DeviceState deviceState =
                    this.getCore_().getDeviceState(this.getCore_().getUserCurrentDevice(uid));

            DeviceLog deviceLog = new DeviceLog();
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
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/cmd: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.NOT_IMPLEMENTED.value(), cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/position/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDevicePosition(@PathVariable("token") String token,
                                            @PathVariable("uid") long uid,
                                            @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DevicePositionRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DevicePositionRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            DevicePositionResponse data = new DevicePositionResponse();
            DeviceState deviceState =
                    this.getCore_().getDeviceState(this.getCore_().getUserCurrentDevice(uid));

            if (deviceState != null) {
                data.setPositionUpdatedAt(ApplicationUtility.getDateTimeInSeconds(deviceState.getPositionLastUpdatedAt()));
                data.setLat(deviceState.getLatitude());
                data.setLng(deviceState.getLongitude());
                data.setSpeed(deviceState.getSpeed());
                data.setAccuracy(deviceState.getDop());
            }

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/position: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/tracking/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDevicePositionTracking(@PathVariable("token") String token,
                                                    @PathVariable("uid") long uid,
                                                    @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceTrackingRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceTrackingRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            Device device = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));

            Timestamp dateFrom;
            Timestamp dateTo;
            if (request.getFrom() == null && request.getTo() == null) {
                dateFrom = new Timestamp(ApplicationUtility.getDateInSeconds() * 1000);
                dateTo = new Timestamp((ApplicationUtility.getDateInSecondsWithAddDaysCount(1) - 1) * 1000);
            } else {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(request.getFrom() * 1000);

                if (calendar.get(Calendar.HOUR_OF_DAY) != 0)
                    calendar.add(Calendar.HOUR_OF_DAY, 24 - calendar.get(Calendar.HOUR_OF_DAY));

                dateFrom = new Timestamp(calendar.getTimeInMillis());


                calendar.setTimeInMillis(request.getTo() * 1000);

                if (calendar.get(Calendar.HOUR_OF_DAY) != 23)
                    calendar.add(Calendar.HOUR_OF_DAY, 23 - calendar.get(Calendar.HOUR_OF_DAY));

                dateTo = new Timestamp(calendar.getTimeInMillis());
            }

            List<DevicePositionDao.DevisePositionTracking> data =
                    this.getCore_().getDevicePositionDao().getDevisePositionTrackingList(
                            device.getId(),
                            dateFrom,
                            dateTo
                    );


            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/tracking: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/edit/kick_user/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity deleteUser(@PathVariable("token") String token,
                                     @PathVariable("uid") long uid,
                                     @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceDeleteUserRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceDeleteUserRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            Device device = this.getCore_().getDao().find(Device.class, "imei", request.getDeviceImei());
            if (device == null)
                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.NOT_FOUND.value(), "Неверный IMEI", null));

            User user = this.getCore_().getDao().find(User.class, "phone", request.getPhoneNum());
            if (user == null)
                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.NOT_FOUND.value(), "Неверный телефон", null));

            this.getCore_().getUserDeviceDao().delete(user.getId(), device.getId());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/edit/kick_user: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/edit/settings/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity editSettings(@PathVariable("token") String token,
                                       @PathVariable("uid") long uid,
                                       @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceEditSettingsRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceEditSettingsRequest.class);

            UserSettings userSettings = this.getCore_().getSettings(uid);
            if (request.getAutostartRuntime() != null)
                userSettings.setAutoStartRuntime(request.getAutostartRuntime());

            this.getCore_().getDao().save(userSettings);

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/edit/settings: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/master_add/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity addMaster(@PathVariable("token") String token,
                                    @PathVariable("uid") long uid,
                                    @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceAddMasterRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceAddMasterRequest.class);

            User user = this.getCore_().getDao().find(User.class, uid);
            if (user != null && user.getTypeId() == 3 && new BCryptPasswordEncoder().matches(request.getPassword(), user.getPassword())) {
                Device device = this.getCore_().getDeviceDao().getDevice(request.getSerialNumber());
                if (device == null)
                    return ResponseEntity.status(HttpStatus.OK).body(
                            new BaseResponse(HttpStatus.NOT_FOUND.value(), "Неверный серийный номер", null));

                if (!device.getMasterModeStatus())
                    return ResponseEntity.status(HttpStatus.OK).body(
                            new BaseResponse(HttpStatus.NOT_FOUND.value(), "Не активирован режим мастера", null));

                UserDevice userDevice = new UserDevice();
                userDevice.setUserId(uid);
                userDevice.setDeviceId(device.getId());
                userDevice.setCarNumber(StringUtils.trimToEmpty(request.getCarNumber()));
                userDevice.setCarModel("Master Mode");
                userDevice.setCarBrand(StringUtils.trimToEmpty(request.getKitName()));
                userDevice.setConfirmed(true);
                userDevice.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                this.getCore_().getDao().save(userDevice);
            } else
                throw new UserNotFoundException(HttpStatus.OK.value());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /device/master_add: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/master_add: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/history/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDeviceHistory(@PathVariable("token") String token,
                                           @PathVariable("uid") long uid,
                                           @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceHistoryRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appKey, requestBody),
                            DeviceHistoryRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            Device device = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));

            Timestamp dateFrom;
            Timestamp dateTo;
            if (request.getFrom() == null && request.getTo() == null) {
                dateFrom = new Timestamp(ApplicationUtility.getDateInSeconds() * 1000);
                dateTo = new Timestamp((ApplicationUtility.getDateInSecondsWithAddDaysCount(1) - 1) * 1000);
            } else {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(request.getFrom() * 1000);

                if (calendar.get(Calendar.HOUR_OF_DAY) != 0)
                    calendar.add(Calendar.HOUR_OF_DAY, 24 - calendar.get(Calendar.HOUR_OF_DAY));

                dateFrom = new Timestamp(calendar.getTimeInMillis());


                calendar.setTimeInMillis(request.getTo() * 1000);

                if (calendar.get(Calendar.HOUR_OF_DAY) != 23)
                    calendar.add(Calendar.HOUR_OF_DAY, 23 - calendar.get(Calendar.HOUR_OF_DAY));

                dateTo = new Timestamp(calendar.getTimeInMillis());
            }

            List<DevicePositionDao.DevisePositionTracking> data =
                    this.getCore_().getDevicePositionDao().getDevisePositionTrackingList(
                            device.getId(),
                            dateFrom,
                            dateTo
                    );


            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/history: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }
}