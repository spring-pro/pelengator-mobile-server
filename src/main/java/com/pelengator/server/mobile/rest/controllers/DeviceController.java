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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

                device = this.getCore_().getDao().find(Device.class, request.getDeviceId());
            } else {
                device = this.getCore_().getDao().find(Device.class,
                        this.getCore_().getUserCurrentDevice(uid).getId());
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
            data.put("activation", activation);
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
                device.setKitMaintenanceDate(new Date(ApplicationUtility.getDateInSecondsWithAddYearsCount(1)));

            UserDevice userDevice = new UserDevice();
            userDevice.setUserId(uid);
            userDevice.setDeviceId(device.getId());
            userDevice.setCarBrand(request.getCarBrand());
            userDevice.setCarModel(request.getCarModel());
            userDevice.setCarNumber(request.getCarNumber());
            userDevice.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            userDevice.setConfirmed(false);

            try {
                this.getCore_().getDao().save(userDevice);
            } catch (ConstraintViolationException cve) {
                throw new DataAlreadyExistsException(HttpStatus.OK.value(),
                        "Устройство с заданным IMEI уже привязано к текущему пользователю!");
            }

            data.setConfirmType("pass");
            data.setDeviceId(userDevice.getId());

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

            UserDevice userDevice = this.getCore_().getDao().find(UserDevice.class, request.getDeviceId());

            if (userDevice != null) {
                Device device = this.getCore_().getDao().find(Device.class, userDevice.getDeviceId());

                if (device != null) {
                    if (device.getPassword().equals(request.getConfirm())) {
                        userDevice.setConfirmed(true);
                        this.getCore_().getDao().save(userDevice);
                    }
                }
            }

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

            this.getCore_().setUserCurrentDevice(uid, Long.parseLong(request.getImei()));

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", null));
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
            DeviceSettingsResponse data = gson.fromJson("{\"buttons\":{\"bottom\":[{\"id\":201,\"v\":1},{\"id\":208,\"v\":1},{\"id\":209,\"v\":1},{\"id\":205,\"v\":1},{\"id\":206,\"v\":1},{\"id\":202,\"v\":1}],\"main\":[{\"enable\":1,\"id\":19,\"need_pin\":false,\"long_press\":true,\"button_settings\":[{\"status\":0,\"cmd\":\"/device/cmd/arm_on\",\"name\":\"Включить охрану\"},{\"status\":1,\"cmd\":\"\",\"name\":\"Ожидание ответа от сервера\"},{\"status\":2,\"cmd\":\"/device/cmd/arm_off\",\"name\":\"Выключить охрану\"}]},{\"enable\":1,\"id\":6,\"need_pin\":false,\"long_press\":true,\"button_settings\":[{\"status\":0,\"cmd\":\"/device/cmd/sos_on\",\"name\":\"Включить sos\"},{\"status\":1,\"cmd\":\"\",\"name\":\"Ожидание ответа от сервера\"},{\"status\":2,\"cmd\":\"/device/cmd/sos_off\",\"name\":\"Выключить sos\"}]},{\"enable\":0,\"id\":1,\"need_pin\":false,\"long_press\":true,\"button_settings\":[{\"status\":0,\"cmd\":\"/device/cmd/engine_on\",\"name\":\"Включить двигатель\"},{\"status\":1,\"cmd\":\"\",\"name\":\"Ожидание ответа от сервера\",\"image\":\"1_2\",\"sound\":\"\"},{\"status\":2,\"cmd\":\"/device/cmd/engine_off\",\"name\":\"Выключить двигатель\"}]},{\"enable\":1,\"id\":5,\"need_pin\":true,\"long_press\":true,\"button_settings\":[{\"status\":0,\"cmd\":\"/device/cmd/block_on\",\"name\":\"Включить блокировку\"},{\"status\":1,\"cmd\":\"\",\"name\":\"Ожидание ответа от сервера\"},{\"status\":2,\"cmd\":\"/device/cmd/block_off\",\"name\":\"Выключить блокировку\"}]},{\"enable\":1,\"id\":3,\"need_pin\":false,\"long_press\":false,\"name\":\"Местоположение\",\"button_action\":\"show_position\",\"image\":\"3_1\"},{\"enable\":1,\"id\":12,\"need_pin\":false,\"long_press\":true,\"button_settings\":[{\"status\":0,\"cmd\":\"/device/cmd/alarm_on\",\"name\":\"Включить тревогу\"},{\"status\":1,\"cmd\":\"\",\"name\":\"Ожидание ответа от сервера\"},{\"status\":2,\"cmd\":\"/device/cmd/alarm_off\",\"name\":\"Выключить тревогу\"}]},{\"enable\":1,\"id\":5,\"need_pin\":true,\"long_press\":true,\"button_settings\":[{\"status\":0,\"cmd\":\"/device/cmd/block_on\",\"name\":\"Включить блокировку\"},{\"status\":1,\"cmd\":\"\",\"name\":\"Ожидание ответа от сервера\"},{\"status\":2,\"cmd\":\"/device/cmd/block_off\",\"name\":\"Выключить блокировку\"}]},{\"enable\":1,\"id\":18,\"need_pin\":false,\"long_press\":false,\"name\":\"История\",\"button_action\":\"show_history\",\"image\":\"18_1\"},{\"enable\":1,\"id\":4,\"need_pin\":false,\"long_press\":true,\"button_settings\":[{\"status\":0,\"cmd\":\"/device/cmd/service_on\",\"name\":\"Включить сервисный режим\"},{\"status\":1,\"cmd\":\"\",\"name\":\"Ожидание ответа от сервера\"},{\"status\":2,\"cmd\":\"/device/cmd/service_off\",\"name\":\"Выключить сервисный режим\"}]},{\"enable\":1,\"id\":17,\"need_pin\":false,\"long_press\":false,\"name\":\"Инструкция\",\"button_action\":\"show_manual\",\"image\":\"17_1\"},{\"enable\":1,\"id\":13,\"need_pin\":false,\"long_press\":false,\"name\":\"Микрофон\",\"button_action\":\"show_microphone\",\"image\":\"13_1\"},{\"enable\":1,\"id\":2,\"need_pin\":false,\"long_press\":false,\"name\":\"Трекинг\",\"button_action\":\"show_tracking\",\"image\":\"2_1\"}]},\"balance_in_menu\":true,\"autostart_runtime\":10,\"all_buttons\":[{\"id\":19,\"v\":1},{\"id\":6,\"v\":1},{\"id\":1,\"v\":1},{\"id\":8,\"v\":1},{\"id\":3,\"v\":1},{\"id\":12,\"v\":1},{\"id\":5,\"v\":1},{\"id\":18,\"v\":1},{\"id\":4,\"v\":1},{\"id\":17,\"v\":1},{\"id\":13,\"v\":1},{\"id\":2,\"v\":1}]}", DeviceSettingsResponse.class);

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
                    "        \"state_id\": 1,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 6,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 1,\n" +
                    "        \"state_id\": 0,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 5,\n" +
                    "        \"state_id\": 1,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 3,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 12,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 5,\n" +
                    "        \"state_id\": 1,\n" +
                    "        \"enable\": 1\n" +
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

            DeviceState deviceState =
                    this.getCore_().getDeviceState(this.getCore_().getUserCurrentDevice(uid).getId());

            if (deviceState != null) {
                Device device = this.getCore_().getDevice(deviceState.getDeviceId());
                Payment payment = this.getCore_().getPaymentTelematics(deviceState.getDeviceId());

                Map<String, Map<String, Object>> cmdIpProgress =
                        this.getCore_().getDeviceCmdInProgress().get(deviceState.getDeviceId());

                data.getButtons().get("main").forEach(item -> {
                    if (19 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStat(cmdIpProgress, item, "arm",
                                deviceState.isArmStatus());
                    } else if (1 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStat(cmdIpProgress, item, "engine",
                                deviceState.isTachometerStatus());
                    } else if (5 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStat(cmdIpProgress, item, "block",
                                deviceState.isAhyStatus());
                    } else if (4 == Math.round((Double) item.get("id"))) {
                        this.getCore_().getCmdBtnStat(cmdIpProgress, item, "service",
                                deviceState.isValetStatus());
                    }
                });

                int kitMaintenanceStateDays = device.getKitMaintenanceDate() == null ? 0 :
                        ((int) ((ApplicationUtility.getDateInSecondsWithAddMonthCount(
                                device.getKitMaintenanceDate(), 12)
                                - ApplicationUtility.getDateInSeconds()) / (60 * 60 * 24)));

                int payFullPeriodDays = getPayTelematicsFullPeriodDays(payment);
                int payStateDays = getPayTelematicsStateDays(payment, payFullPeriodDays);

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
                        item.put("percent", deviceState.getGpsQuality() * 100 / 15);
                    } else if (202 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 202);
                        item.put("text", String.format("%.2f", (deviceState.getBatteryPower())) + " v");
                        item.put("percent", Math.round((deviceState.getBatteryPower()) * 100 / 4.24));
                    }
                });
                data.getButtons().put("bottom", bottomButtonsList);
            }

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
            Device device = this.getCore_().getUserCurrentDevice(uid);

            CommandHistory commandHistory = new CommandHistory();
            commandHistory.setCommand(cmd);
            commandHistory.setDeviceType(1);
            commandHistory.setDeviceId(device.getId());
            commandHistory.setSenderId(uid);
            commandHistory.setSenderType(CommandHistory.CommandSenderTypeEnum.USER.name());
            commandHistory.setSent(false);
            commandHistory.setErrMsg("");
            commandHistory.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
            commandHistory.setUpdatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
            this.getCore_().getDao().save(commandHistory);

            BaseCmdResponse response = new BaseCmdResponse();

            switch (cmd) {
                case "engine_on": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_ENGINE_START.toString(StandardCharsets.ISO_8859_1)));
                    getCore_().addDeviceCmdInProgress(device.getId(), "engine", false);
                    break;
                }
                case "engine_off": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_ENGINE_STOP.toString(StandardCharsets.ISO_8859_1)));
                    getCore_().addDeviceCmdInProgress(device.getId(), "engine", true);
                    break;
                }
                case "arm_on": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_ARM_ENABLE.toString(StandardCharsets.ISO_8859_1)));
                    getCore_().addDeviceCmdInProgress(device.getId(), "arm", false);
                    break;
                }
                case "arm_off": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_ARM_DISABLE.toString(StandardCharsets.ISO_8859_1)));
                    getCore_().addDeviceCmdInProgress(device.getId(), "arm", true);
                    break;
                }
                case "block_on": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_ENGINE_LOCK.toString(StandardCharsets.ISO_8859_1)));
                    getCore_().addDeviceCmdInProgress(device.getId(), "block", false);
                    break;
                }
                case "block_off": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_ENGINE_UNLOCK.toString(StandardCharsets.ISO_8859_1)));
                    getCore_().addDeviceCmdInProgress(device.getId(), "block", true);
                    break;
                }
                case "alarm_on": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_SEARCH_CAR.toString(StandardCharsets.ISO_8859_1)));
                    break;
                }
                case "service_on": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_SERVICE_ENABLE.toString(StandardCharsets.ISO_8859_1)));
                    getCore_().addDeviceCmdInProgress(device.getId(), "service", false);
                    break;
                }
                case "service_off": {
                    response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), commandHistory.getId(),
                            AutofonCommands.AUTOFON_CMD_SERVICE_DISABLE.toString(StandardCharsets.ISO_8859_1)));
                    getCore_().addDeviceCmdInProgress(device.getId(), "service", true);
                    break;
                }
                default: {
                    throw new UnknownCommandException(HttpStatus.OK.value());
                }
            }

            if (response.getCode() == HttpStatus.OK.value()) {
                Map<String, String> data = new HashMap<>(1);
                data.put("answer", "Команда отправлена!");

                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.OK.value(), "", data));
            } else {
                throw new Exception(response.getMessage());
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
                    this.getCore_().getDeviceState(this.getCore_().getUserCurrentDevice(uid).getId());

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

            DeviceState deviceState =
                    this.getCore_().getDeviceState(this.getCore_().getUserCurrentDevice(uid).getId());

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
                            deviceState.getDeviceId(),
                            dateFrom,
                            dateTo
                    );

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/state: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }
}