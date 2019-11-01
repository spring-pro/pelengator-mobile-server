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

import com.pelengator.server.utils.ApplicationUtility;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;

public class Test {

    public static void main(String[] args) {
        try {
            System.out.println(ApplicationUtility.dateToFormattedString(new Date(), "yyyy-MM-dd HH:mm:ss"));
            System.out.println(ApplicationUtility.getDateTimeInSeconds(new Date()) * 1000);
            System.out.println(ApplicationUtility.secondsToFormattedString(
                    1571742997L,
                    ApplicationUtility.GMT_3,
                    "yyyy-MM-dd HH:mm:ss"
            ));
            System.out.println(ApplicationUtility.getDateInSeconds(new Date()));
            System.out.println(new Timestamp(ApplicationUtility.getDateInSeconds() * 1000));
            System.out.println(new Timestamp(ApplicationUtility.getDateInSecondsWithAddDaysCount(1) * 1000));
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
}
