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

package com.pelengator.server.mobile;

import com.pelengator.server.dao.postgresql.*;
import com.pelengator.server.dao.postgresql.dto.DeviceStateForMobile;
import com.pelengator.server.dao.postgresql.entity.*;
import com.pelengator.server.exception.mobile.TokenExpiredException;
import com.pelengator.server.hazelcast.HazelcastClient;
import com.pelengator.server.utils.ApplicationUtility;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Core {

    private static final Logger LOGGER = getLogger(Core.class.getSimpleName());

    private String kafkaAddress;
    private static boolean isIsDebugMode;
    private String gatewayCmdURL;

    private Map<Long, Integer> userSmsMapCacheL3 = new ConcurrentHashMap<>();
    private Map<String, Long> tokenUserMapCacheL3 = new ConcurrentHashMap<>();
    private Map<Long, Device> userCurrentDeviceCacheL3 = new ConcurrentHashMap<>();
    private Map<Long, UserDevice> userDeviceAddRequestCacheL3 = new ConcurrentHashMap<>();
    private Map<Long, Map<String, Map<String, Object>>> deviceCmdInProgress = new ConcurrentHashMap<>();

    private Dao dao = new Dao("/opt/pelengator/mobile-server/conf/hibernate.cfg.xml");
    private UserDao userDao = new UserDao();
    private UserDeviceDao userDeviceDao = new UserDeviceDao();
    private DeviceStateDao deviceStateDao = new DeviceStateDao();
    private DevicePositionDao devicePositionDao = new DevicePositionDao();
    private DialogDao dialogDao = new DialogDao();
    private UserTokenDao userTokenDao = new UserTokenDao();

    private String hazelcastServers;
    private HazelcastClient hazelcastClient;

    public static Logger getLogger(String className) {
        Logger l = Logger.getLogger(className);
        if (isIsDebugMode)
            l.setLevel(Level.DEBUG);
        return l;
    }

    public void init() {
        try {
            hazelcastClient = new HazelcastClient(hazelcastServers);
            hazelcastClient.connect();

            restoreUserTokensFromDB();
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
        }
    }

    public String registerUserToken(Long userId) throws Exception {
        String token = ApplicationUtility.getToken(String.valueOf(userId));
        userTokenDao.addToken(userId, token, null);
        tokenUserMapCacheL3.put(token, userId);
        return token;
    }

    public Long getUserIdByToken(String token) throws Exception {
        Long userId = tokenUserMapCacheL3.get(token);
        if (userId == null)
            throw new TokenExpiredException(HttpStatus.UNAUTHORIZED.value());
        return userId;
    }

    public void setUserCurrentDevice(long userId, long imei) throws Exception {
        Device device = dao.find(Device.class, "imei", imei);
        if (device == null)
            throw new TokenExpiredException(HttpStatus.OK.value());
        userCurrentDeviceCacheL3.put(userId, device);
    }

    public Device getUserCurrentDevice(long userId) throws Exception {
        return userCurrentDeviceCacheL3.get(userId);
    }

    public Device getDevice(long deviceId) throws Exception {
        Device device = hazelcastClient.getDevice(deviceId);
        if (device == null)
            device = dao.find(Device.class, deviceId);

        return device;
    }

    public DeviceState getDeviceState(long deviceId) throws Exception {
        DeviceState deviceState = hazelcastClient.getDeviceState(deviceId);
        if (deviceState == null)
            deviceState = dao.find(DeviceState.class, deviceId);

        return deviceState;
    }

    public Payment getPaymentTelematics(long deviceId) throws Exception {
        Payment payment = hazelcastClient.getPaymentTelematics(deviceId);
        if (payment == null)
            payment = dao.getPaymentByDeviceAndPayType(deviceId, Payment.PAY_TYPE_TELEMATICS);

        return payment;
    }

    public Payment getPaymentActivation(long deviceId) throws Exception {
        Payment payment = hazelcastClient.getPaymentTelematics(deviceId);
        if (payment == null)
            payment = dao.getPaymentByDeviceAndPayType(deviceId, Payment.PAY_TYPE_ACTIVATION);

        return payment;
    }

    public void addUserDeviceRequest(Long deviceId, UserDevice userDevice) throws Exception {
        userDeviceAddRequestCacheL3.put(deviceId, userDevice);
    }

    public void restoreUserTokensFromDB() {
        long time0 = System.currentTimeMillis();
        Integer count = null;

        try {
            List<UserToken> userTokenList = dao.get(UserToken.class);
            for (UserToken userToken : userTokenList) {
                tokenUserMapCacheL3.put(userToken.getToken(), userToken.getUserId());
            }
            count = tokenUserMapCacheL3.size();
        } catch (Throwable t) {
            LOGGER.error(t);
        }

        if (count == null) {
            LOGGER.error("DB[user_token] read: fail");
        } else {
            long time = System.currentTimeMillis() - time0;
            String detailedTime = time / 1000 >= 1 ? (time / 1000 + "s " + time % 1000) + "ms" : time + "ms";
            LOGGER.info("DB[user_token] read: " + count + ", " + detailedTime);
        }
    }

    public void addDeviceCmdInProgress(long deviceId, String btnCmd, boolean oldSate) {
        Map<String, Map<String, Object>> cmdInProgress = new HashMap<>(1);
        Map<String, Object> cmdDetails = new HashMap<>(2);
        cmdDetails.put("oldSate", oldSate);
        cmdDetails.put("sentAt", System.currentTimeMillis());
        cmdInProgress.put(btnCmd, cmdDetails);
        getDeviceCmdInProgress().put(deviceId, cmdInProgress);
    }

    public Map<String, Object> getCmdBtnStat(Map<String, Map<String, Object>> cmdIpProgress, Map<String, Object> item,
                                             String btnCmd, Boolean btnState) {
        if (cmdIpProgress != null) {
            Map<String, Object> armCmd = cmdIpProgress.get(btnCmd);
            if (armCmd != null) {
                if (armCmd.get("oldSate") == btnState
                        && (System.currentTimeMillis() - (Long) armCmd.get("sentAt")) < 30000) {
                    item.put("state_id", 1);
                } else {
                    item.put("state_id", btnState ? 2 : 0);
                    cmdIpProgress.remove(btnCmd);
                }
            } else item.put("state_id", btnState ? 2 : 0);
        } else item.put("state_id", btnState ? 2 : 0);

        return item;
    }

    public void stop() {
    }

    public String getKafkaAddress() {
        return kafkaAddress;
    }

    public void setKafkaAddress(String kafkaAddress) {
        this.kafkaAddress = kafkaAddress;
    }

    public void setIsDebugMode(boolean isDebugMode) {
        isIsDebugMode = isDebugMode;
    }

    public String getGatewayCmdURL() {
        return gatewayCmdURL;
    }

    public void setGatewayCmdURL(String gatewayCmdURL) {
        this.gatewayCmdURL = gatewayCmdURL;
    }

    public String getCookieByName(HttpServletRequest request, String cookieName) {
        if (cookieName == null) {
            return "";
        }

        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return "";
    }

    public Map<Long, Integer> getUserSmsMapCacheL3() {
        return userSmsMapCacheL3;
    }

    public Dao getDao() {
        return dao;
    }

    public UserDao getUserDao() {
        return userDao;
    }

    public UserDeviceDao getUserDeviceDao() {
        return userDeviceDao;
    }

    public DeviceStateDao getDeviceStateDao() {
        return deviceStateDao;
    }

    public DevicePositionDao getDevicePositionDao() {
        return devicePositionDao;
    }

    public DialogDao getDialogDao() {
        return dialogDao;
    }

    public String getHazelcastServers() {
        return hazelcastServers;
    }

    public void setHazelcastServers(String hazelcastServers) {
        this.hazelcastServers = hazelcastServers;
    }

    public HazelcastClient getHazelcastClient() {
        return hazelcastClient;
    }

    public Map<Long, Map<String, Map<String, Object>>> getDeviceCmdInProgress() {
        return deviceCmdInProgress;
    }
}