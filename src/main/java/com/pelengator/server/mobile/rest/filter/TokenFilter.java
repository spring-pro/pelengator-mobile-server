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

package com.pelengator.server.mobile.rest.filter;

import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.rest.ErrorResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TokenFilter implements Filter {

    private static final Logger LOGGER = Core.getLogger(TokenFilter.class.getSimpleName());

    private Core core_;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String apiMethodName = request.getPathInfo();
        try {
            LOGGER.debug("Request -> " + request.getPathInfo());
            System.out.println("Request -> " + request.getPathInfo());

            request.setCharacterEncoding("UTF-8");
            response.setCharacterEncoding("UTF-8");

            if (!apiMethodName.equals("/check_versions")
                    && !apiMethodName.equals("/user/login")
                    && !apiMethodName.equals("/user/set")
                    && !apiMethodName.equals("/user/get/sms_code")
                    && !apiMethodName.equals("/user/confirm")) {

                String token = core_.getCookieByName(request, "PHPSESSID");

                if (!StringUtils.isBlank(token)) {
                    request.getRequestDispatcher(request.getPathInfo()
                            .concat("/" + token).concat("/" + this.getCore_().getUserIdByToken(token)))
                            .forward(request, response);
                } else {
                    unauthorized(response);
                }
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
            }
        } catch (Throwable cause) {
            LOGGER.error("Request ERROR: ", cause);
            unauthorized(response);
        }
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        try {
            response.setHeader("Content-Type", "application/json");
            response.setStatus(HttpStatus.OK.value());
            response.getWriter().write(new ErrorResponse(HttpStatus.NON_AUTHORITATIVE_INFORMATION.value(),
                    "Время сессии истекло!").json());
            response.getWriter().flush();
            response.getWriter().close();
        } catch (Throwable cause) {
            LOGGER.error("Request ERROR: ", cause);
        }
    }

    @Override
    public void destroy() { }

    public Core getCore_() {
        return core_;
    }

    public void setCore_(Core core_) {
        this.core_ = core_;
    }
}
