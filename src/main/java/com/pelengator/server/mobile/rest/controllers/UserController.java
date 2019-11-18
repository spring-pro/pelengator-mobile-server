/*
 * Copyright (c) 2019 Spring-Pro
 * Moscow, Russia
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of Spring-Pro. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Spring-Pro.
 *
 * Developer: Maxim Zemskov, https://www.linkedin.com/in/mzemskov/
 */

package com.pelengator.server.mobile.rest.controllers;

import com.pelengator.server.dao.postgresql.DeviceDao;
import com.pelengator.server.dao.postgresql.entity.User;
import com.pelengator.server.dao.postgresql.entity.UserPushToken;
import com.pelengator.server.exception.mobile.UserNotFoundException;
import com.pelengator.server.exception.mobile.WrongSmsCodeException;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.ErrorResponse;
import com.pelengator.server.mobile.rest.entity.BaseEntity;
import com.pelengator.server.mobile.rest.entity.request.user.ConfirmRequest;
import com.pelengator.server.mobile.rest.entity.request.user.SMSCodeRequest;
import com.pelengator.server.mobile.rest.entity.request.user.UserLoginRequest;
import com.pelengator.server.mobile.rest.entity.request.user.UserSetRequest;
import com.pelengator.server.mobile.rest.entity.response.user.*;
import com.pelengator.server.exception.mobile.BaseException;
import com.pelengator.server.exception.mobile.UnknownException;
import com.pelengator.server.utils.ApplicationUtility;
import com.pelengator.server.utils.sms.SmsSender;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController extends BaseController {

    private static final Logger LOGGER = Core.getLogger(UserController.class.getSimpleName());

    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity userLogin(@RequestParam(name = "d", defaultValue = "") String requestBody) {
        try {
            UserLoginRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody), UserLoginRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            User user = this.getCore_().getDao().find(User.class, "phone", request.getPhoneNum());

            if (user == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            int smsCode = ApplicationUtility.generateRandomInt(1000, 9999);

            LOGGER.debug("SMS code: " + smsCode + " for user: " + user.getPhone());

            SmsSender smsSender = new SmsSender(user.getPhone(), "Pelengator confirm code: " + smsCode);
            if (!smsSender.send())
                throw new UnknownException(HttpStatus.OK.value());

            this.getCore_().getUserSmsMapCacheL3().put(user.getId(), smsCode);

            UserLoginResponse data = new UserLoginResponse();
            data.setUserId(user.getId());

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /user/login: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /user/login: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage()));
        }
    }

    @RequestMapping(value = "/set", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity userSet(@RequestParam(name = "d", defaultValue = "") String requestBody) {
        try {
            UserSetRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody), UserSetRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            User user = this.getCore_().getDao().find(User.class, "phone", request.getLogin());

            if (user == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            UserSetResponse data = new UserSetResponse();
            data.setSid(this.getCore_().registerUserToken(user));
            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /user/set: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /user/set: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/sms_code", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getSMSCode(@RequestParam(name = "d", defaultValue = "") String requestBody) {
        try {
            SMSCodeRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody), SMSCodeRequest.class);

            if (request == null || StringUtils.isBlank(request.getPhoneNum()))
                throw new UnknownException(HttpStatus.OK.value());

            User user = this.getCore_().getDao().find(User.class, "phone", request.getPhoneNum());

            if (user == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            int smsCode = ApplicationUtility.generateRandomInt(1000, 9999);

            LOGGER.debug("SMS code: " + smsCode + " for user: " + user.getPhone());

            SmsSender smsSender = new SmsSender(user.getPhone(), "Pelengator confirm code: " + smsCode);
            if (!smsSender.send())
                throw new UnknownException(HttpStatus.OK.value());

            this.getCore_().getUserSmsMapCacheL3().put(user.getId(), smsCode);

            SMSCodeResponse data = new SMSCodeResponse();
            data.setUserId(user.getId());

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /get/sms_code: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /get/sms_code: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage()));
        }
    }

    @RequestMapping(value = "/confirm", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity confirmSMSCode(@RequestParam(name = "d", defaultValue = "") String requestBody) {
        try {
            ConfirmRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody), ConfirmRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            User user = this.getCore_().getDao().find(User.class, request.getUserId());

            if (user == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            Integer smsCode = this.getCore_().getUserSmsMapCacheL3().remove(request.getUserId());
            if (smsCode == null || !smsCode.equals(request.getSmsCode())) {
                throw new WrongSmsCodeException(HttpStatus.OK.value());
            }

            ConfirmResponse data = new ConfirmResponse();
            data.setSid(this.getCore_().registerUserToken(user));

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /confirm: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /confirm: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/config/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getUserConfig(@PathVariable("token") String token,
                                        @PathVariable("uid") long uid,
                                        @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            if (requestBody == null)
                throw new UnknownException(HttpStatus.OK.value());

            List<DeviceDao.UserConfigForMobileEntity> alarmDevicesList =
                    this.getCore_().getDeviceDao().getUserConfigForMobile(uid);

            List<Map> alarmDevicesResultList = new ArrayList<>();
            int index = 0;
            for (DeviceDao.UserConfigForMobileEntity item : alarmDevicesList) {
                Map<String, Object> alarmDeviceItem = new HashMap<>();
                alarmDeviceItem.put("index", ++index);
                alarmDeviceItem.put("id", item.getDeviceId().toString());
                alarmDeviceItem.put("imei", item.getImei().toString());
                alarmDeviceItem.put("name", item.getCarBrand());
                alarmDeviceItem.put("model", item.getCarModel());
                alarmDeviceItem.put("gosnomer", item.getCarNumber());
                alarmDeviceItem.put("add_date", item.getCreatedAt().toString());
                alarmDeviceItem.put("complect_name", "");
                alarmDeviceItem.put("serial_number", item.getSerialNumber());
                alarmDeviceItem.put("phone_number", item.getPhoneNumber());
                alarmDeviceItem.put("access_type", "3");
                alarmDeviceItem.put("pay_status", 1);
                alarmDeviceItem.put("connected_users", new String[0]);
                alarmDevicesResultList.add(alarmDeviceItem);
            }

            UserGetConfigResponse data = new UserGetConfigResponse();
            data.getAlarmDevices().addAll(alarmDevicesResultList);
            //data.getSosPhones().add("222222222");

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /get/config: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /get/config: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage()));
        }
    }

    @RequestMapping(value = "/edit/sos/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity editSos(@PathVariable("token") String token,
                                  @PathVariable("uid") long uid,
                                  @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            if (requestBody == null)
                throw new UnknownException(HttpStatus.OK.value());

            System.out.println("/edit/sos -> " + ApplicationUtility.decrypt(appAndroidKey, requestBody));


            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /edit/sos: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /edit/sos: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage()));
        }
    }

    @RequestMapping(value = "/edit/token/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity editToken(@PathVariable("token") String token,
                                    @PathVariable("uid") long uid,
                                    @RequestParam(name = "d", defaultValue = "") String requestBody) {

        try {
            UserEditTokenRequest request =
                    BaseEntity.objectV1_0(ApplicationUtility.decrypt(appAndroidKey, requestBody), UserEditTokenRequest.class);

            if (request == null)
                throw new UnknownException(HttpStatus.OK.value());

            UserPushToken userPushToken = this.getCore_().getDao().find(UserPushToken.class, uid);

            if (userPushToken == null) {
                userPushToken = new UserPushToken();
                userPushToken.setUserId(uid);
            }

            userPushToken.setDevice(request.getOs().toUpperCase().contains("ANDROID") ?
                    UserPushToken.tokenDevice.ANDROID.name() : UserPushToken.tokenDevice.IOS.name());
            userPushToken.setToken(request.getFmsId());
            this.getCore_().getDao().save(userPushToken);

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> /edit/token: " + e.getMessage());
            return ResponseEntity.status(e.getCode()).body(
                    new ErrorResponse(e.getLocalCode(), e.getMessage()));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> /edit/token: ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), cause.getMessage()));
        }
    }
}
