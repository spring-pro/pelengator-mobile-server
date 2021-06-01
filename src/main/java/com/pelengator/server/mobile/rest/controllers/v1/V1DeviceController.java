/*
 * Copyright (c) 2020 Spring-Pro
 * Moscow, Russia
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of Spring-Pro. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Spring-Pro.
 *
 * Author: Maxim Zemskov, https://www.linkedin.com/in/mzemskov/
 */

package com.pelengator.server.mobile.rest.controllers.v1;

import com.google.gson.Gson;
import com.pelengator.server.autofon.AutofonCommands;
import com.pelengator.server.dao.postgresql.dto.ConnectedUsersForMobile;
import com.pelengator.server.dao.postgresql.dto.DeviceUserHistoryRow;
import com.pelengator.server.dao.postgresql.dto.DevisePositionTracking;
import com.pelengator.server.dao.postgresql.dto.DialogMessageMobileEntity;
import com.pelengator.server.dao.postgresql.entity.*;
import com.pelengator.server.exception.mobile.*;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.dto.ChangeUserSmsMapCacheL3Object;
import com.pelengator.server.mobile.kafka.TransportCommandObject;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.ErrorResponse;
import com.pelengator.server.mobile.rest.controllers.BaseController;
import com.pelengator.server.mobile.rest.entity.BaseEntity;
import com.pelengator.server.mobile.rest.entity.request.device.*;
import com.pelengator.server.mobile.rest.entity.request.v1.device.DeviceSetRequest;
import com.pelengator.server.mobile.rest.entity.response.BaseCmdResponse;
import com.pelengator.server.mobile.rest.entity.response.device.*;
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

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;

@RestController
@RequestMapping("/v1/device")
public class V1DeviceController extends BaseController {

    private static final Logger LOGGER = Core.getLogger(V1DeviceController.class.getSimpleName());

    @RequestMapping(value = "/get/available_payments/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getAvailablePayments(HttpServletRequest servletRequest,
                                               @PathVariable("token") String token,
                                               @PathVariable("uid") long uid,
                                               @RequestBody String requestBody) {

        try {
            Device device;

            if (!StringUtils.isBlank(requestBody)) {
                DeviceGetAvailablePaymentsRequest request =
                        BaseEntity.objectV1_0(requestBody, DeviceGetAvailablePaymentsRequest.class);

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
            activation.put("text", device.getIsActivated() ? "" : "30000 р.");
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
                    telematics.put("text", payStateDays < 0 ? 0 : payStateDays + "дн.");
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
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/add/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity addDevice(HttpServletRequest servletRequest,
                                    @PathVariable("token") String token,
                                    @PathVariable("uid") long uid,
                                    @RequestBody DeviceAddRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            DeviceAddResponse data = new DeviceAddResponse();
            Device device = this.getCore_().getDao().find(Device.class, "imei", request.getDeviceImei());

            if (device == null)
                throw new IncorrectIMEIException(HttpStatus.OK.value());

            if (device.getKitMaintenanceDate() == null)
                device.setKitMaintenanceDate(new Date(ApplicationUtility.getDateInSecondsWithAddYearsCount(1) * 1000));

            List<UserDevice> userDeviceList = this.getCore_().getUserDeviceDao().getUserDeviceList(device.getId(), 1, true);

            if (userDeviceList != null && userDeviceList.size() > 0) {
                User user = this.getCore_().getDao().find(User.class, uid);
                List<ConnectedUsersForMobile> connectedUsers = this.getCore_().getUserDeviceDao().getConnectedUsers(device.getId(), true);
                int confirmCode = ApplicationUtility.generateRandomInt6Digits(100000, 999999);
                this.getCore_().getUserDeviceConfirmMapCacheL3().put(uid + "_" + device.getId(), String.valueOf(confirmCode));
                if (!SmsSender.send(connectedUsers.get(0).getPhone(),
                        "Пользователь " + user.getPhone() + " пытается добавить автомобиль гос. номер " + request.getCarNumber() + ". " +
                                "Код подтверждения: " + confirmCode))
                    throw new UnknownException(HttpStatus.OK.value());
                data.setConfirmType("sms");
            } else {
                data.setConfirmType("pass");
            }

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

            data.setDeviceId(device.getId());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/add_confirm/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity addDeviceConfirm(HttpServletRequest servletRequest,
                                           @PathVariable("token") String token,
                                           @PathVariable("uid") long uid,
                                           @RequestBody DeviceAddConfirmRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            if (StringUtils.isBlank(request.getConfirm()))
                throw new IncorrectDevicePasswordException(HttpStatus.OK.value());

            List<UserDevice> userDeviceList = this.getCore_().getUserDeviceDao().getUserDeviceList(request.getDeviceId(), 1, true);
            UserDevice userDevice = this.getCore_().getUserDeviceDao().getUserDevice(uid, request.getDeviceId(), false);

            if (userDevice != null) {
                Device device = this.getCore_().getDao().find(Device.class, userDevice.getDeviceId());

                if (device != null) {
                    String confirmPassword = device.getPassword();
                    if (userDeviceList != null && userDeviceList.size() > 0) {
                        confirmPassword = this.getCore_().getUserDeviceConfirmMapCacheL3().remove(uid + "_" + device.getId());
                    }

                    if (confirmPassword != null && confirmPassword.equals(request.getConfirm())) {
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
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/edit/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity editDevice(HttpServletRequest servletRequest,
                                     @PathVariable("token") String token,
                                     @PathVariable("uid") long uid,
                                     @RequestBody DeviceEditRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            UserDevice userDevice = this.getCore_().getUserDeviceDao().getUserDevice(uid, request.getDeviceImei());

            if (userDevice != null) {
                userDevice.setCarBrand(request.getCarBrand());
                userDevice.setCarModel(request.getCarModel());
                userDevice.setCarNumber(request.getCarNumber());
                this.getCore_().getDao().save(userDevice);
            } else throw new IncorrectIMEIException(HttpStatus.OK.value());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/delete/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity deleteDevice(HttpServletRequest servletRequest,
                                       @PathVariable("token") String token,
                                       @PathVariable("uid") long uid,
                                       @RequestBody DeviceDeleteRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            UserDevice userDevice = this.getCore_().getUserDeviceDao().getUserDevice(uid, request.getDeviceImei());

            if (userDevice != null) {
                this.getCore_().getDao().delete(userDevice);
            } else throw new IncorrectIMEIException(HttpStatus.OK.value());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/set/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity deviceSet(HttpServletRequest servletRequest,
                                    @PathVariable("token") String token,
                                    @PathVariable("uid") long uid,
                                    @RequestBody DeviceSetRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            Device device = this.getCore_().setUserCurrentDevice(uid, Long.parseLong(request.getImei()));

            DeviceState deviceState = this.getCore_().getDeviceState(device.getId());

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
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/current/get/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity deviceCurrentGet(HttpServletRequest servletRequest,
                                           @PathVariable("token") String token,
                                           @PathVariable("uid") long uid) {

        try {

            Device currentDevice = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));
            DeviceCurrentGetResponse data = new DeviceCurrentGetResponse();

            if (currentDevice != null)
                data.setImei(currentDevice.getImei());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/settings/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDeviceSettings(HttpServletRequest servletRequest,
                                            @PathVariable("token") String token,
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
                    "            \"id\": 5,\n" +
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
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
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
    public ResponseEntity getDeviceState(HttpServletRequest servletRequest,
                                         @PathVariable("token") String token,
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
                    "        \"state_id\": 0,\n" +
                    "        \"enable\": 0\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 3,\n" +
                    "        \"enable\": 1\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": 12,\n" +
                    "        \"state_id\": 0,\n" +
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
                    "        \"id\": 5,\n" +
                    "        \"state_id\": 0,\n" +
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
            Device currentDevice = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));
            if (currentDevice == null)
                // If current device is not set
                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.REQUEST_TIMEOUT.value(), "Ведутся технические работы, Подождите, пожалуйста ...", data));

            if (currentDevice.getTypeId() == 1)
                /* ------------------------------ For AutoPhone device ------------------------------ */
                buttonsProcessAutoPhoneDevice(data, token, currentDevice);
            else if (currentDevice.getTypeId() == 2)
                /* ------------------------------ For StartFone device ------------------------------ */
                buttonsProcessStartFoneDevice(data, token, currentDevice);

            List<DialogMessageMobileEntity> messages = this.getCore_().getUnreadChatMessagesFromCacheL2(uid);
            if (messages != null)
                data.getMessages().addAll(messages);

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/cmd/{cmd}/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity sendCmd(HttpServletRequest servletRequest,
                                  @PathVariable("cmd") String cmd,
                                  @PathVariable("token") String token,
                                  @PathVariable("uid") long uid) {

        try {
            Device currentDevice = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));

            if (currentDevice.getTypeId() == 1)
                /* ------------------------------ For AutoPhone device ------------------------------ */
                return sendCmdAutoPhoneDevice(currentDevice, cmd, uid);
            else if (currentDevice.getTypeId() == 2)
                /* ------------------------------ For StartFone device ------------------------------ */
                return sendCmdStartFoneDevice(currentDevice, cmd, uid);
            else
                throw new UnknownCommandException(HttpStatus.OK.value());
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.NOT_IMPLEMENTED.value(), cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/position/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDevicePosition(HttpServletRequest servletRequest,
                                            @PathVariable("token") String token,
                                            @PathVariable("uid") long uid,
                                            @RequestBody DevicePositionRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            DevicePositionResponse data = new DevicePositionResponse();
            Device device = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));
            DeviceState deviceState = this.getCore_().getDeviceState(device.getId());

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
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/tracking/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDevicePositionTracking(HttpServletRequest servletRequest,
                                                    @PathVariable("token") String token,
                                                    @PathVariable("uid") long uid,
                                                    @RequestBody DeviceTrackingRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

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

            List<DevisePositionTracking> data =
                    this.getCore_().getDevicePositionDao().getDevisePositionTrackingList(
                            device.getId(),
                            dateFrom,
                            dateTo
                    );


            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/edit/kick_user/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity deleteUser(HttpServletRequest servletRequest,
                                     @PathVariable("token") String token,
                                     @PathVariable("uid") long uid,
                                     @RequestBody DeviceDeleteUserRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

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
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/edit/settings/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity editSettings(HttpServletRequest servletRequest,
                                       @PathVariable("token") String token,
                                       @PathVariable("uid") long uid,
                                       @RequestBody DeviceEditSettingsRequest request) {

        try {
            UserSettings userSettings = this.getCore_().getSettings(uid);
            if (request.getAutostartRuntime() != null)
                userSettings.setAutoStartRuntime(request.getAutostartRuntime());

            this.getCore_().getDao().save(userSettings);

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/master_add/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity addMaster(HttpServletRequest servletRequest,
                                    @PathVariable("token") String token,
                                    @PathVariable("uid") long uid,
                                    @RequestBody DeviceAddMasterRequest request) {

        try {
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
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/history/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDeviceHistory(HttpServletRequest servletRequest,
                                           @PathVariable("token") String token,
                                           @PathVariable("uid") long uid,
                                           @RequestBody DeviceHistoryRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

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

            List<DeviceUserHistoryRow> deviseUserHistoryList =
                    this.getCore_().getDeviceUserHistoryDao().getDeviseUserHistoryList(
                            device.getId(),
                            dateFrom,
                            dateTo
                    );

            Map<String, Object> data = new HashMap<>(1);
            List<Map<String, Object>> dataList = new ArrayList<>();
            List<DeviceUserHistoryRow> actionsList = new ArrayList<>();
            long lastDateTimeInMinutes = 0;
            long lastDateTimeInMinutesForCompare = 0;
            boolean isLastItem = false;
            for (int i = 0; i < deviseUserHistoryList.size(); i++) {
                if (i == (deviseUserHistoryList.size() - 1)) {
                    isLastItem = true;
                }

                Calendar tsMinutes = Calendar.getInstance();
                tsMinutes.setTimeInMillis(deviseUserHistoryList.get(i).getCreatedAt() * 1000);
                tsMinutes.set(Calendar.SECOND, 0);
                tsMinutes.set(Calendar.MILLISECOND, 0);

                if (!isLastItem && lastDateTimeInMinutesForCompare > 0 && tsMinutes.getTimeInMillis() / 1000 > lastDateTimeInMinutesForCompare) {
                    Map<String, Object> dataRow = new HashMap<>(2);
                    dataRow.put("ts", lastDateTimeInMinutes);
                    dataRow.put("actions", actionsList);
                    dataList.add(dataRow);

                    actionsList = new ArrayList<>();
                    actionsList.add(deviseUserHistoryList.get(i));
                } else if (isLastItem) {
                    if (tsMinutes.getTimeInMillis() / 1000 > lastDateTimeInMinutesForCompare) {
                        Map<String, Object> dataRow = new HashMap<>(2);
                        dataRow.put("ts", lastDateTimeInMinutes);
                        dataRow.put("actions", actionsList);
                        dataList.add(dataRow);
                        actionsList = new ArrayList<>();
                    }

                    actionsList.add(deviseUserHistoryList.get(i));

                    Map<String, Object> dataRow = new HashMap<>(2);
                    dataRow.put("ts", tsMinutes.getTimeInMillis() / 1000);
                    dataRow.put("actions", actionsList);
                    dataList.add(dataRow);
                } else
                    actionsList.add(deviseUserHistoryList.get(i));

                lastDateTimeInMinutes = tsMinutes.getTimeInMillis() / 1000;
                lastDateTimeInMinutesForCompare = ApplicationUtility.getDateInSecondsWithAddMinutesCount(
                        new Date(tsMinutes.getTimeInMillis()), 1);
            }

            data.put("history", dataList);
            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/change_user/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity changeUser(HttpServletRequest servletRequest,
                                     @PathVariable("token") String token,
                                     @PathVariable("uid") long uid,
                                     @RequestBody DeviceChangeUserRequest request) {

        try {
            User user = this.getCore_().getDao().find(User.class, uid);
            if (user == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            Device device = this.getCore_().getDao().find(Device.class, "imei", request.getDeviceImei());
            if (device == null)
                throw new IncorrectIMEIException(HttpStatus.OK.value());

            User oldUser = this.getCore_().getDao().find(User.class, "phone", request.getOldPhoneNum());
            if (oldUser == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            User newUser = this.getCore_().getDao().find(User.class, "phone", request.getNewPhoneNum());
            if (newUser == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            if (!Objects.equals(user.getId(), oldUser.getId()))
                throw new UserHasNoPermissionsToChangeDeviceException(HttpStatus.OK.value());

            UserDevice masterUserDevice = this.getCore_().getUserDeviceDao().getMasterUserDevice(device.getId());
            if (masterUserDevice == null || !masterUserDevice.getUserId().equals(oldUser.getId()))
                throw new UserHasNoPermissionsToChangeDeviceException(HttpStatus.OK.value());

            if (StringUtils.isBlank(request.getSmsCode())) {
                int smsCode = getSmsCode(oldUser);
                if (!SmsSender.send(user.getPhone(), "Pelengator change auto user code: " + smsCode))
                    throw new UnknownException(HttpStatus.OK.value());

                this.getCore_().getChangeUserSmsMapCacheL3().put(request.getDeviceImei(),
                        ChangeUserSmsMapCacheL3Object.getJson(
                                request.getOldPhoneNum(), request.getNewPhoneNum(), Integer.toString(smsCode)));

                Map<String, String> data = new HashMap<>(1);
                data.put("smsCode", Integer.toString(smsCode));

                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.OK.value(), "", data));
            } else {
                if (request.getSmsCode().equals(ChangeUserSmsMapCacheL3Object.getInstance(
                        this.getCore_().getChangeUserSmsMapCacheL3().remove(request.getDeviceImei())
                ).getSmsCode())) {

                    this.getCore_().getUserDeviceDao().changeDeviceMasterUser(masterUserDevice.getId(), device.getId(), newUser.getId());
                } else {
                    this.getCore_().getChangeUserSmsMapCacheL3().remove(request.getDeviceImei());
                    throw new WrongSmsCodeException(HttpStatus.OK.value());
                }

                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.OK.value(), "", null));
            }
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage()));
        }
    }
}