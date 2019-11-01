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
import com.pelengator.server.dao.postgresql.DevicePositionDao;
import com.pelengator.server.dao.postgresql.DeviceStateDao;
import com.pelengator.server.dao.postgresql.entity.Device;
import com.pelengator.server.dao.postgresql.entity.UserDevice;
import com.pelengator.server.exception.mobile.BaseException;
import com.pelengator.server.exception.mobile.DataAlreadyExistsException;
import com.pelengator.server.exception.mobile.IncorrectIMEIException;
import com.pelengator.server.exception.mobile.UnknownException;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.ErrorResponse;
import com.pelengator.server.mobile.rest.entity.BaseEntity;
import com.pelengator.server.mobile.rest.entity.request.device.*;
import com.pelengator.server.mobile.rest.entity.response.device.DeviceAddResponse;
import com.pelengator.server.mobile.rest.entity.response.device.DevicePositionResponse;
import com.pelengator.server.mobile.rest.entity.response.device.DeviceSettingsResponse;
import com.pelengator.server.mobile.rest.entity.response.device.DeviceStateResponse;
import com.pelengator.server.utils.ApplicationUtility;
import org.apache.log4j.Logger;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/device")
public class DeviceController extends BaseController {

    private static final Logger LOGGER = Core.getLogger(DeviceController.class.getSimpleName());

    @RequestMapping(value = "/add/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity addDevice(@PathVariable("token") String token,
                                    @PathVariable("uid") long uid,
                                    @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DeviceAddRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody),
                            DeviceAddRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            DeviceAddResponse data = new DeviceAddResponse();
            Device device = this.getCore_().getUserCurrentDevice(uid);

            if (device != null) {
                if (!device.getImei().equals(request.getDeviceImei()))
                    throw new IncorrectIMEIException(HttpStatus.OK.value());

                UserDevice userDevice = new UserDevice();
                userDevice.setUserId(uid);
                userDevice.setDeviceId(device.getId());
                userDevice.setCarBrand(request.getCarBrand());
                userDevice.setCarModel(request.getCarModel());
                userDevice.setCarNumber(request.getCarNumber());
                userDevice.setCarMaintenanceDate(new Date(System.currentTimeMillis()));
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
            }

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.debug("REQUEST error -> /device/set: " + e.getMessage());
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
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody),
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
            LOGGER.debug("REQUEST error -> /device/set: " + e.getMessage());
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
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody),
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
            LOGGER.debug("REQUEST error -> /device/set: " + e.getMessage());
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
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody),
                            DeviceDeleteRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            UserDevice userDevice = this.getCore_().getUserDeviceDao().getUserDevice(uid, request.getDeviceImei());

            if (userDevice != null) {
                this.getCore_().getDao().delete(userDevice);
            } else throw new IncorrectIMEIException(HttpStatus.OK.value());

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.debug("REQUEST error -> /device/set: " + e.getMessage());
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
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody), DeviceSetRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            this.getCore_().setUserCurrentDevice(uid, Long.parseLong(request.getImei()));

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.debug("REQUEST error -> /device/set: " + e.getMessage());
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
     * @param token - token from thw header (cookie at the time ...)
     * @param uid   - user id
     * @return - result json
     */

    @RequestMapping(value = "/get/state/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDeviceState(@PathVariable("token") String token,
                                         @PathVariable("uid") long uid) {

        try {
            String stateTemp = "{\"data_ts\":1571137306,\"buttons\":{\"bottom\":[{\"icon_id\":201,\"text\":\"0,00 v\",\"percent\":0,\"enable\":1},{\"icon_id\":208,\"text\":\"0 дн.\",\"percent\":0,\"enable\":1},{\"icon_id\":209,\"text\":\"0 дн.\",\"percent\":0,\"enable\":1},{\"icon_id\":205,\"text\":\"умеренно\",\"percent\":0,\"enable\":1},{\"icon_id\":206,\"text\":\"без связи\",\"percent\":0,\"enable\":1},{\"icon_id\":202,\"text\":\"0,00 v\",\"percent\":0,\"enable\":1}],\"main\":[{\"id\":19,\"state_id\":2,\"enable\":1},{\"id\":6,\"state_id\":2,\"enable\":1},{\"id\":1,\"state_id\":0,\"enable\":0},{\"id\":5,\"state_id\":0,\"enable\":1},{\"id\":3,\"enable\":1},{\"id\":12,\"enable\":1},{\"id\":5,\"state_id\":0,\"enable\":1},{\"id\":18,\"enable\":1},{\"id\":4,\"state_id\":0,\"enable\":1},{\"id\":17,\"enable\":1},{\"id\":13,\"enable\":1},{\"id\":2,\"enable\":1}]},\"test_status\":{\"stat\":0},\"all_statuses\":{\"service\":false},\"messages\":[]}";

            DeviceStateResponse data = gson.fromJson(stateTemp, DeviceStateResponse.class);
            List<Map<String, Object>> bottomButtonsList = data.getButtons().get("bottom");

            DeviceStateDao.DeviceStateForMobile deviceState = this.getCore_().getUserCurrentDeviceState(uid);

            if (deviceState != null) {
                int carMaintenanceStateDays = (int) (
                        (ApplicationUtility.getDateInSecondsWithAddMonthCount(deviceState.getCarMaintenanceDate(), 12)
                                - ApplicationUtility.getDateInSeconds()) / (60 * 60 * 24));

                int payFullPeriodDays = (int) (ApplicationUtility.getDateInSecondsWithAddMonthCount(
                        deviceState.getPayDate(), deviceState.getPayPeriodMonths())
                        - ApplicationUtility.getDateInSeconds(deviceState.getPayDate())) / (60 * 60 * 24);
                int payStateDays = payFullPeriodDays -
                        ((int) (ApplicationUtility.getDateInSeconds() -
                                ApplicationUtility.getDateInSeconds(deviceState.getPayDate())) / (60 * 60 * 24));

                bottomButtonsList.forEach(item -> {
                    if (201 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 201);
                        item.put("text", String.format("%.2f", (deviceState.getExternalPower())) + " v");
                        item.put("percent", Math.round((deviceState.getExternalPower()) * 100 / 15));
                    } else if (208 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 208);
                        item.put("text", (Math.max(carMaintenanceStateDays, 0)) + " дн.");
                        item.put("percent", Math.min(carMaintenanceStateDays * 100 / 365, 100));
                    } else if (209 == Math.round((Double) item.get("icon_id"))) {
                        item.put("icon_id", 209);
                        item.put("text", (Math.max(payStateDays, 0)) + " дн.");
                        item.put("percent", Math.min(payStateDays * 100 / payFullPeriodDays, 100));
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

    @RequestMapping(value = "/get/position/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getDevicePosition(@PathVariable("token") String token,
                                            @PathVariable("uid") long uid,
                                            @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            DevicePositionRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody),
                            DevicePositionRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            DevicePositionResponse data = new DevicePositionResponse();
            DeviceStateDao.DeviceStateForMobile deviceState = this.getCore_().getUserCurrentDeviceState(uid);

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
            LOGGER.error("REQUEST error -> /device/get/state: ", cause);
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
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody),
                            DeviceTrackingRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            DeviceStateDao.DeviceStateForMobile deviceState = this.getCore_().getUserCurrentDeviceState(uid);

            Timestamp dateFrom;
            Timestamp dateTo;
            if (request.getFrom() == null && request.getTo() == null) {
                dateFrom = new Timestamp(ApplicationUtility.getDateInSeconds() * 1000);
                dateTo = new Timestamp((ApplicationUtility.getDateInSecondsWithAddDaysCount(1) - 1) * 1000);
            } else {
                dateFrom = new Timestamp(request.getFrom() * 1000);
                dateTo = new Timestamp(request.getTo() * 1000);
            }

            List<DevicePositionDao.DevisePositionTracking> data =
                    this.getCore_().getDevicePositionDao().getDevisePositionTrackingList(
                            deviceState.getDeviceId(),
                            dateFrom,
                            dateTo
                    );

            System.out.println(gson.toJson(data));

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /device/get/state: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }
}