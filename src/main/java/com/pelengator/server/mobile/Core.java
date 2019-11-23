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
import com.pelengator.server.dao.postgresql.entity.Device;
import com.pelengator.server.dao.postgresql.entity.User;
import com.pelengator.server.dao.postgresql.entity.UserDevice;
import com.pelengator.server.exception.mobile.TokenExpiredException;
import com.pelengator.server.hazelcast.HazelcastClient;
import com.pelengator.server.utils.ApplicationUtility;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Core {

    private static final Logger LOGGER = getLogger(Core.class.getSimpleName());

    private String kafkaAddress;
    private String memcachedServers;
    private static boolean isIsDebugMode;
    private String gatewayCmdURL;

    private Map<Long, Integer> userSmsMapCacheL3 = new ConcurrentHashMap<>();
    private Map<String, User> tokenUserMapCacheL3 = new ConcurrentHashMap<>();
    private Map<Long, Device> userCurrentDeviceCacheL3 = new ConcurrentHashMap<>();
    private Map<Long, UserDevice> userDeviceAddRequestCacheL3 = new ConcurrentHashMap<>();

    private Dao dao = new Dao("/opt/pelengator/mobile-server/conf/hibernate.cfg.xml");
    private DeviceDao deviceDao = new DeviceDao();
    private UserDeviceDao userDeviceDao = new UserDeviceDao();
    private DeviceStateDao deviceStateDao = new DeviceStateDao();
    private DevicePositionDao devicePositionDao = new DevicePositionDao();
    private DialogDao dialogDao = new DialogDao();

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
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
        }
    }

    public String registerUserToken(User user) throws Exception {
        String token = ApplicationUtility.getToken(String.valueOf(user.getId()));
        tokenUserMapCacheL3.put(token, user);
        return token;
    }

    public long getUserIdByToken(String token) throws Exception {
        User user = tokenUserMapCacheL3.get(token);
        if (user == null)
            throw new TokenExpiredException(HttpStatus.OK.value());
        return user.getId();
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

    public DeviceStateForMobile getUserCurrentDeviceState(long userId) throws Exception {
        Device device = userCurrentDeviceCacheL3.get(userId);
        if (device != null) {
            DeviceStateForMobile deviceStateForMobile = hazelcastClient.getDeviceStateForMobile(device.getId());
            if (deviceStateForMobile == null)
                deviceStateForMobile = deviceStateDao.getDeviceState(userId, device.getId());
            return deviceStateForMobile;
        } else return null;
    }

    public void addUserDeviceRequest(Long deviceId, UserDevice userDevice) throws Exception {
        userDeviceAddRequestCacheL3.put(deviceId, userDevice);
    }

    public void stop() {
        if (hazelcastClient != null)
            hazelcastClient.shutdown();
    }

    public String getKafkaAddress() {
        return kafkaAddress;
    }

    public void setKafkaAddress(String kafkaAddress) {
        this.kafkaAddress = kafkaAddress;
    }

    public void setMemcachedServers(String memcachedServers) {
        this.memcachedServers = memcachedServers;
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

    public DeviceDao getDeviceDao() {
        return deviceDao;
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
}