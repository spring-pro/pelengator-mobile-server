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

import com.pelengator.server.dao.postgresql.UserDao;
import com.pelengator.server.dao.postgresql.dto.ConnectedUsersForMobile;
import com.pelengator.server.dao.postgresql.entity.Device;
import com.pelengator.server.dao.postgresql.entity.User;
import com.pelengator.server.dao.postgresql.entity.UserPushToken;
import com.pelengator.server.dao.postgresql.entity.UserSettings;
import com.pelengator.server.exception.mobile.*;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.ErrorResponse;
import com.pelengator.server.mobile.rest.controllers.BaseController;
import com.pelengator.server.mobile.rest.entity.request.v1.user.ConfirmRequest;
import com.pelengator.server.mobile.rest.entity.request.v1.user.SMSCodeRequest;
import com.pelengator.server.mobile.rest.entity.request.v1.user.UserLoginRequest;
import com.pelengator.server.mobile.rest.entity.request.v1.user.UserSetRequest;
import com.pelengator.server.mobile.rest.entity.response.user.*;
import com.pelengator.server.utils.ApplicationUtility;
import com.pelengator.server.utils.sms.SmsSender;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
import java.util.*;

@RestController
@RequestMapping("/v1/user")
public class V1UserController extends BaseController {

    private static final Logger LOGGER = Core.getLogger(V1UserController.class.getSimpleName());

    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity userLogin(HttpServletRequest servletRequest,
                                    @RequestBody UserLoginRequest request) {
        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            User user = this.getCore_().getDao().find(User.class, "phone", request.getPhoneNum());

            if (user == null) {
                user = new User();
                user.setPhone(request.getPhoneNum());
                user.setFullName("");
                user.setTypeId(1L);
                user.setComment("");
                user.setPassword("");
                user.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));

                getCore_().getDao().save(user);
            }

            user.setAccountNum(
                    ApplicationUtility.milliSecondsToFormattedString(
                            ApplicationUtility.getCurrentTimeStampGMT_0(), ApplicationUtility.GMT_0, "yyMMdd")
                            .concat(String.format("%06d", user.getId())));
            getCore_().getDao().save(user);

            int smsCode = getSmsCode(user);

            if (!SmsSender.send(user.getPhone(), "Pelengator confirm code: " + smsCode))
                throw new UnknownException(HttpStatus.OK.value());

            this.getCore_().getUserSmsMapCacheL3().put(user.getPhone(), smsCode);

            UserLoginResponse data = new UserLoginResponse();
            data.setUserId(user.getId());

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
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

    @RequestMapping(value = "/set", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity userSet(HttpServletRequest servletRequest,
                                  HttpServletResponse response,
                                  @RequestBody UserSetRequest request) {
        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            User user = this.getCore_().getDao().find(User.class, "phone", request.getLogin());

            if (user == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            String token = this.getCore_().registerUserToken(user);

            UserSetResponse data = new UserSetResponse();
            data.setSid(token);

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
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

    @RequestMapping(value = "/get/sms_code", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getSMSCode(HttpServletRequest servletRequest,
                                     @RequestBody SMSCodeRequest request) {
        try {
            if (request == null || StringUtils.isBlank(request.getPhoneNum()))
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            User user = this.getCore_().getDao().find(User.class, "phone", request.getPhoneNum());

            if (user == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            int smsCode = getSmsCode(user);

            if (!SmsSender.send(user.getPhone(), "Pelengator confirm code: " + smsCode))
                throw new UnknownException(HttpStatus.OK.value());

            this.getCore_().getUserSmsMapCacheL3().put(user.getPhone(), smsCode);

            SMSCodeResponse data = new SMSCodeResponse();
            data.setUserId(user.getId());

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
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

    @RequestMapping(value = "/confirm", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity confirmSMSCode(HttpServletRequest servletRequest,
                                         HttpServletResponse response,
                                         @RequestBody ConfirmRequest request) {
        try {

            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            User user = this.getCore_().getDao().find(User.class, request.getUserId());

            if (user == null)
                throw new UserNotFoundException(HttpStatus.OK.value());

            Integer smsCode = this.getCore_().getUserSmsMapCacheL3().remove(user.getPhone());
            if (smsCode == null || !smsCode.equals(request.getSmsCode())) {
                throw new WrongSmsCodeException(HttpStatus.OK.value());
            }

            String token = this.getCore_().registerUserToken(user);

            ConfirmResponse data = new ConfirmResponse();
            data.setSid(token);

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
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

    @RequestMapping(value = "/get/config/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getUserConfig(HttpServletRequest servletRequest,
                                        @PathVariable("token") String token,
                                        @PathVariable("uid") long uid) {

        try {
            List<UserDao.UserConfigForMobileEntity> alarmDevicesList =
                    this.getCore_().getUserDao().getUserConfigForMobile(uid);

            UserSettings userSettings = this.getCore_().getSettings(uid);
            List<Map> alarmDevicesResultList = new ArrayList<>();
            int index = 0;
            for (UserDao.UserConfigForMobileEntity item : alarmDevicesList) {

                List<List<Object>> connectedUsers = new ArrayList<>();
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

                List<ConnectedUsersForMobile> connectedUsersForMobileList = this.getCore_().getUserDeviceDao().getConnectedUsers(item.getDeviceId());
                if (connectedUsersForMobileList != null && connectedUsersForMobileList.size() > 0) {
                    if (uid == connectedUsersForMobileList.get(0).getId()) {
                        for (ConnectedUsersForMobile connectedUsersForMobile : connectedUsersForMobileList) {
                            List<Object> deviceConnectedUsersList = new ArrayList<>(3);
                            deviceConnectedUsersList.add(connectedUsersForMobile.getPhone());
                            deviceConnectedUsersList.add(ApplicationUtility.milliSecondsToFormattedString(
                                    connectedUsersForMobile.getCreatedAt().getTime(),
                                    ApplicationUtility.GMT_3, ApplicationUtility.DATE_FORMAT));
                            connectedUsers.add(deviceConnectedUsersList);
                        }
                    }
                }
                alarmDeviceItem.put("connected_users", connectedUsers);
                alarmDevicesResultList.add(alarmDeviceItem);
            }

            UserGetConfigResponse data = new UserGetConfigResponse();
            data.getAlarmDevices().addAll(alarmDevicesResultList);

            if (userSettings != null && !StringUtils.isBlank(userSettings.getSosPhones()))
                data.getSosPhones().addAll(Arrays.asList(userSettings.getSosPhones().split(",", -1)));
            else
                data.getSosPhones().addAll(Arrays.asList("", "", ""));

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
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

    @RequestMapping(value = "/edit/sos/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity editSos(HttpServletRequest servletRequest,
                                  @PathVariable("token") String token,
                                  @PathVariable("uid") long uid,
                                  @RequestBody UserEditSosRequest request) {

        try {
            UserSettings userSettings = this.getCore_().getSettings(uid);

            String[] sosPhones = StringUtils.isBlank(userSettings.getSosPhones()) ? new String[]{"", "", ""} :
                    userSettings.getSosPhones().split(",", -1);

            if (request.getPhoneIndex0() != null)
                sosPhones[0] = StringUtils.trimToEmpty(request.getPhoneIndex0());
            if (request.getPhoneIndex1() != null)
                sosPhones[1] = StringUtils.trimToEmpty(request.getPhoneIndex1());
            if (request.getPhoneIndex2() != null)
                sosPhones[2] = StringUtils.trimToEmpty(request.getPhoneIndex2());

            userSettings.setSosPhones(StringUtils.join(sosPhones, ","));
            this.getCore_().getDao().save(userSettings);

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", null));
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

    @RequestMapping(value = "/edit/token/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity editToken(HttpServletRequest servletRequest,
                                    @PathVariable("token") String token,
                                    @PathVariable("uid") long uid,
                                    @RequestBody UserEditTokenRequest request) {

        try {
            UserPushToken userPushToken = this.getCore_().getDao().find(UserPushToken.class, "userId", uid);
            User user = this.getCore_().getDao().find(User.class, uid);

            if (userPushToken != null && StringUtils.isBlank(request.getFmsId())) {
                if (user.getTypeId() == 3 && this.getCore_().getUserCurrentDevice(uid) > 0) {
                    Device currentDevice = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));
                    this.getCore_().getUserDeviceDao().delete(uid, currentDevice.getId());
                }

                this.getCore_().getDao().delete(userPushToken);
            } else {
                if (userPushToken == null) {
                    userPushToken = new UserPushToken();
                    userPushToken.setUserId(uid);
                }

                switch (StringUtils.trimToEmpty(request.getOs()).toUpperCase()) {
                    case "ANDROID":
                        userPushToken.setDevice(UserPushToken.tokenDevice.ANDROID.name());
                        break;
                    case "IOS":
                        userPushToken.setDevice(UserPushToken.tokenDevice.IOS.name());
                        break;
                    default:
                        throw new UnknownDeviceException(HttpStatus.OK.value());
                }

                userPushToken.setToken(request.getFmsId());
                this.getCore_().getDao().save(userPushToken);
            }

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", null));
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
