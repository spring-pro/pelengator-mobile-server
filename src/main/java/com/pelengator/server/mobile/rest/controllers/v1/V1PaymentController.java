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

import com.pelengator.server.autofon.AutofonCommands;
import com.pelengator.server.dao.postgresql.dto.DialogMessageMobileEntity;
import com.pelengator.server.dao.postgresql.entity.*;
import com.pelengator.server.exception.mobile.BaseException;
import com.pelengator.server.exception.mobile.PaymentNotFoundException;
import com.pelengator.server.exception.mobile.RequestBodyIsEmptyException;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.kafka.TransportCommandObject;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.ErrorResponse;
import com.pelengator.server.mobile.rest.controllers.BaseController;
import com.pelengator.server.mobile.rest.entity.request.payment.PaymentGetUrlRequest;
import com.pelengator.server.mobile.rest.entity.response.BaseCmdResponse;
import com.pelengator.server.mobile.rest.entity.response.payment.PaymentStatusDataResponse;
import com.pelengator.server.utils.ApplicationConstants;
import com.pelengator.server.utils.ApplicationUtility;
import com.pelengator.server.utils.DeviceLogger;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/v1/payment")
public class V1PaymentController extends BaseController {

    private static final Logger LOGGER = Core.getLogger(V1PaymentController.class.getSimpleName());

    @RequestMapping(value = "/get/url/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getPaymentUrl(HttpServletRequest servletRequest,
                                        @PathVariable("token") String token,
                                        @PathVariable("uid") long uid,
                                        @RequestBody PaymentGetUrlRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            int payType = 0;
            switch (request.getType()) {
                case PAYMENT_TYPE_CLIENT_ACTIVATION:
                    payType = Payment.PAY_TYPE_ACTIVATION;
                    break;
                case PAYMENT_TYPE_CLIENT_TELEMATICS:
                    payType = Payment.PAY_TYPE_TELEMATICS;
                    break;
            }

            Device device = this.getCore_().getDevice(this.getCore_().getUserCurrentDevice(uid));

            String paymentToken = ApplicationUtility.getToken(
                    String.valueOf(uid)
                            .concat(String.valueOf(device.getImei()))
                            .concat(String.valueOf(ApplicationUtility.getCurrentTimeStampGMT_0())));

            Payment payment =
                    this.getCore_().getPaymentDao().getPayment(device.getId(), payType, Payment.PAY_STATUS_CREATED);

            if (payment == null) {
                payment = new Payment();
                payment.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
            }
            payment.setUserId(uid);
            payment.setDeviceId(device.getId());
            payment.setStatus(Payment.PAY_STATUS_CREATED);
            payment.setAmount(0.0f);
            payment.setPayPeriodMonths(0);
            payment.setPayType(payType);
            payment.setToken(paymentToken);
            payment.setUpdatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
            this.getCore_().getDao().save(payment);

            Map<String, String> data = new HashMap<>(1);
            data.put("url", HTTP_SERVER_URL + "/payment/payment_page?t=" + paymentToken);

            return ResponseEntity.status(HttpStatus.OK).body(new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/status",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity paymentStatus(HttpServletRequest servletRequest,
                                        @RequestParam(name = "InvoiceId", defaultValue = "0") String invoiceId,
                                        @RequestParam(name = "AccountId", defaultValue = "") String accountId,
                                        @RequestParam(name = "Description", defaultValue = "") String description,
                                        @RequestParam(name = "Data") String paymentData,
                                        @RequestParam(name = "Amount") Float amount,
                                        @RequestParam(name = "Status") String status) {

        Map<String, Integer> data = new HashMap<>(1);
        try {
            Device device = null;

            Payment payment = this.getCore_().getPaymentDao().getPayment(
                    Long.parseLong(invoiceId), accountId, Payment.PAY_STATUS_CREATED);

            if (payment == null) {
                data.put("code", 13);
                return ResponseEntity.status(HttpStatus.OK).body(true);
            }

            PaymentStatusDataResponse statusDataResponse = null;
            if (!StringUtils.isBlank(paymentData)) {
                statusDataResponse = gson.fromJson(paymentData, PaymentStatusDataResponse.class);
            }

            int payFullPeriodDays = 0;
            int payStateDays = 0;
            Payment paymentPayed = this.getCore_().getPaymentTelematics(payment.getDeviceId());
            if (paymentPayed != null) {
                payFullPeriodDays = getPayTelematicsFullPeriodDays(paymentPayed);
                payStateDays = getPayTelematicsStateDays(paymentPayed, payFullPeriodDays);
            }

            payment.setAmount(amount);
            payment.setComment(description);

            switch (status) {
                case "Authorized":
                    payment.setStatus(Payment.PAY_STATUS_AUTHORIZED);
                    break;
                case "Completed":
                    /*device = this.getCore_().getDao().find(Device.class, payment.getDeviceId());
                    if (device != null) {
                        DeviceLog deviceLog = new DeviceLog();
                        deviceLog.setDeviceId(device.getId());
                        deviceLog.setAdminId(null);
                        deviceLog.setUserId(null);
                        deviceLog.setSenderType(DeviceLog.CommandSenderTypeEnum.SERVER.name());
                        deviceLog.setLogType(DeviceLogger.LOG_TYPE_OUTPUT_EVENT);
                        deviceLog.setEventType(0);
                        deviceLog.setMessage("activationKit");
                        deviceLog.setSent(false);
                        deviceLog.setDescription("");
                        deviceLog.setErrMsg("");
                        deviceLog.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                        deviceLog.setUpdatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                        this.getCore_().getDao().save(deviceLog);

                        BaseCmdResponse response = sendAutofonCmdPost(new TransportCommandObject(device.getImei(), deviceLog.getId(),
                                AutofonCommands.AUTOFON_CMD_ACTIVATION_KIT.toString(StandardCharsets.ISO_8859_1)));

                        if (response.getCode() == HttpStatus.OK.value())
                            device.setIsActivated(true);
                    }*/

                    payment.setStatus(Payment.PAY_STATUS_CASHLESS);

                    if (payStateDays > 0) {
                        payment.setComment("Дата создания платежа смещена с учетом неиспользованных дней (" + payStateDays + " дн.)");
                        payment.setCreatedAt(new Timestamp(ApplicationUtility.getDateInSecondsWithAddDaysCount(payStateDays) * 1000));
                    } else {
                        payment.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                    }

                    Dialog dialog = this.getCore_().getDao().find(Dialog.class, "userId", payment.getUserId());
                    if (dialog == null) {
                        dialog = createDialog(payment.getUserId(), false);
                    } else {
                        dialog.setReadBySupport(false);
                    }
                    dialog.setUpdatedAt(new Timestamp(new Date().getTime()));

                    String messageText = "Оплата произведена успешно. " +
                            " Серийный № " + device.getSerialNumber();

                    DialogMessage dialogMessage;
                    dialogMessage = new DialogMessage();
                    dialogMessage.setDialogId(dialog.getId());
                    dialogMessage.setSenderType(DialogMessage.SENDER_TYPE_SUPPORT);
                    dialogMessage.setSenderId(2L); // User "Pelengator"
                    dialogMessage.setMessageType(DialogMessage.MESSAGE_TYPE_DEFAULT);
                    dialogMessage.setMessage(messageText.trim());
                    dialogMessage.setIsRead(false);
                    dialogMessage.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));

                    Session session = this.getCore_().getDao().beginTransaction();
                    this.getCore_().getDao().save(dialog, session);
                    this.getCore_().getDao().save(dialogMessage, session);
                    this.getCore_().getDao().commitTransaction(session);

                    DialogMessageMobileEntity dialogMessageMobileEntity = new DialogMessageMobileEntity();
                    dialogMessageMobileEntity.setMessageId(dialogMessage.getId());
                    dialogMessageMobileEntity.setMessageText(dialogMessage.getMessage());
                    dialogMessageMobileEntity.setMessageType(dialogMessage.getMessageType());
                    dialogMessageMobileEntity.setSenderType(dialogMessage.getSenderType());
                    dialogMessageMobileEntity.setIsRead(dialogMessage.isIsRead() ? 1 : 0);
                    dialogMessageMobileEntity.setMessageTime(dialogMessage.getCreatedAt().getTime() / 1000);
                    saveUnreadChatMessageToHazelcast(payment.getUserId(), dialogMessageMobileEntity);

                    break;
                case "Cancelled":
                    payment.setStatus(Payment.PAY_STATUS_CANCELLED);
                    break;
                case "Declined":
                    payment.setStatus(Payment.PAY_STATUS_DECLINED);
                    break;
            }

            if (statusDataResponse != null)
                payment.setPayPeriodMonths(statusDataResponse.getPeriod());
            payment.setUpdatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));

            if (device == null) {
                this.getCore_().getDao().save(payment);
            } else {
                Session session = this.getCore_().getDao().beginTransaction();
                this.getCore_().getDao().save(payment, session);
                this.getCore_().getDao().save(device, session);
                this.getCore_().getDao().commitTransaction(session);
            }

            this.getCore_().getHazelcastClient().putPaymentTelematics(payment.getDeviceId(), payment);

            data.put("code", 0);
            return ResponseEntity.status(HttpStatus.OK).body(data);
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            data.put("code", 13);
            return ResponseEntity.status(HttpStatus.OK).body(data);
        }
    }

    @RequestMapping(value = "/payment_page",
            method = RequestMethod.GET, produces = "text/html;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> getPaymentPage(HttpServletRequest servletRequest,
                                                 @RequestParam(name = "t") String token) {

        try {
            Payment payment = this.getCore_().getPaymentDao().getPaymentByToken(token, Payment.PAY_STATUS_CREATED);

            if (payment == null) {
                throw new PaymentNotFoundException(HttpStatus.FORBIDDEN.value());
            }

            User user = this.getCore_().getDao().find(User.class, payment.getUserId());
            Device device = this.getCore_().getDao().find(Device.class, payment.getDeviceId());
            DeviceState deviceState = this.getCore_().getDeviceState(device.getId());
            ComplexIndividualPrice complexIndividualPrice = this.getCore_().getDao().find(ComplexIndividualPrice.class, device.getId());
            DeviceActivations deviceActivations = this.getCore_()
                    .getDeviceActivationsDao()
                    .getLastActivationBySupport(device.getId(), "2021-06-01 00:00:00");

            String htmlSelectOptions = "";
            switch (payment.getPayType()) {
                case Payment.PAY_TYPE_TELEMATICS: {
                    if (complexIndividualPrice != null && complexIndividualPrice.getTelematics() > 0) {
                        htmlSelectOptions += "                           <option value=\"12|" +
                                complexIndividualPrice.getTelematics() + "\" selected>1 год - " +
                                NumberFormat.getNumberInstance(Locale.GERMAN).format(complexIndividualPrice.getTelematics()) +
                                "Р</option>\n";
                    } else {
                        if (!deviceState.isSpr4()
                                && !deviceState.isSpr7()
                                && !deviceState.isSpr12()
                                && !deviceState.isSpr15()
                                && !deviceState.isT15()
                                && deviceState.isT5()
                                && deviceState.isLabelSet()
                                && deviceActivations != null) {
                            htmlSelectOptions += "                           <option value=\"12|10000\" selected>1 год - 10.000Р</option>\n";
                        } else {
                            htmlSelectOptions += "                           <option value=\"12|3900\" selected>1 год - 3.900Р</option>\n";
                        }
                    }
                    break;
                }
                case Payment.PAY_TYPE_ACTIVATION: {
                    switch (device.getKitName()) {
                        case "Pelengator S":
                            htmlSelectOptions += "                           <option value=\"0|30000\" selected>" + device.getKitName() + " - 30.000Р</option>\n";
                            break;
                        case "Pelengator S+":
                            htmlSelectOptions += "                           <option value=\"0|30000\" selected>" + device.getKitName() + " - 30.000Р</option>\n";
                            break;
                        case "СПРИНГ-СТАТИЧЕСКИЙ":
                            htmlSelectOptions += "                           <option value=\"0|30000\" selected>" + device.getKitName() + " - 30.000Р</option>\n";
                            break;
                        case "СПРИНГ-ДИНАМИЧЕСКИЙ":
                            htmlSelectOptions += "                           <option value=\"0|30000\" selected>" + device.getKitName() + " - 30.000Р</option>\n";
                            break;
                        default:
                            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Данное устройство не подлежит активации");
                    }
                    break;
                }
            }

            String html = "<!DOCTYPE html>\n " +
                    "    <html> \n" +
                    "    <head>\n" +
                    "        <meta name=\"viewport\"\n" +
                    "              content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=yes\">\n" +
                    "        <meta name=\"theme-color\" content=\"#1ba1e2\">\n" +
                    "        <link rel=\"stylesheet\" href=\"https://test-pelengator.com:8443/resources/css/style.css\" type=\"text/css\">\n" +
                    "        <script type=\"text/javascript\" src=\"https://code.jquery.com/jquery-3.1.1.min.js\"></script>\n" +
                    "        <link rel=\"stylesheet\"\n" +
                    "              href=\"https://cdnjs.cloudflare.com/ajax/libs/twitter-bootstrap/3.3.7/css/bootstrap.min.css\"\n" +
                    "              type=\"text/css\">\n" +
                    "        <link rel=\"stylesheet\"\n" +
                    "              href=\"https://cdnjs.cloudflare.com/ajax/libs/twitter-bootstrap/3.3.7/css/bootstrap-theme.min.css\"\n" +
                    "              type=\"text/css\">\n" +
                    "        <script src=\"https://cdnjs.cloudflare.com/ajax/libs/twitter-bootstrap/3.3.7/js/bootstrap.min.js\"></script>\n" +
                    "        <script src=\"https://widget.cloudpayments.ru/bundles/cloudpayments\"></script>\n" +
                    "        <script type=\"text/javascript\">\n" +
                    "            this.pay = function () {\n" +
                    "                var widget = new cp.CloudPayments({startWithButton: false});\n" +
                    "                var period_int = 12\n" +
                    "                var receipt = {\n" +
                    "                   Items: [\n" +
                    "                       {\n" +
                    "                           label: getTitle(" + payment.getPayType() + "),\n" +
                    "                           price: getAmount(),\n" +
                    "                           quantity: 1,\n" +
                    "                           amount: getAmount(),\n" +
                    "                           vat: 0,\n" +
                    "                           method: 1\n" +
                    "                       }\n" +
                    "                   ],\n" +
                    "                   taxationSystem: 2,\n" +
                    "                   amounts: {\n" +
                    "                       electronic: getAmount(), \n" +
                    "                       advancePayment: 0.00, \n" +
                    "                       credit: 0.00, \n" +
                    "                       provision: 0.00 \n" +
                    "                   }\n" +
                    "                }\n" +
                    "                widget.charge({\n" +
                    "                        publicId: 'pk_e507e6e555398c43e6cbbd4738860',\n" +
                    "                        description: getTitle(" + payment.getPayType() + "),\n" +
                    "                        amount: getAmount(),\n" +
                    "                        currency: 'RUB',\n" +
                    "                        invoiceId: '" + payment.getId() + "',\n" +
                    "                        accountId: '" + user.getAccountNum() + "',\n" +
                    "                        data: {\n" +
                    "                            period: getPeriod(),\n" +
                    "                            cloudPayments: {\n" +
                    "                               customerReceipt: receipt\n" +
                    "                            }\n" +
                    "                        }\n" +
                    "                    },\n" +
                    "                    function (options) {\n" +
                    "                        // console.log(\"успешно\");\n" +
                    "                    },\n" +
                    "                    function (reason, options) {\n" +
                    "                        // console.log(\"НЕ успешно\");\n" +
                    "                    });\n" +
                    "            };\n" +
                    "            function getTitle(pay_type) {\n" +
                    "               if (pay_type == 0) {\n" +
                    "                   return 'Оплата подписки pelengator сроком на ' + $(\"#period option:selected\").val().split(\"|\", 2)[0] + ' мес.'\n" +
                    "               } else if (pay_type == 1) {\n" +
                    "                   return 'Активация комплекта " + device.getKitName() + "'\n" +
                    "               }\n" +
                    "            }\n" +
                    "            function getPeriod() {\n" +
                    "               return parseInt($(\"#period option:selected\").val().split(\"|\", 2)[0], 10);\n" +
                    "            }\n" +
                    "            function getAmount() {\n" +
                    "               return parseInt($(\"#period option:selected\").val().split(\"|\", 2)[1], 10);\n" +
                    "            }\n" +
                    "            $(document).ready(function () {\n" +
                    "                $('#checkout').click(pay);\n" +
                    "            });\n" +
                    "        </script>\n" +
                    "    </head>\n" +
                    "    <body>\n" +
                    "    <div class=\"container\">\n" +
                    "        <div class=\"row\">\n" +
                    "            <div class=\"col-xs-12 col-sm-4 col-sm-offset-4 payment-wrap\">\n" +
                    "                <img src=\"https://test-pelengator.com:8443/resources/credit-card.png\" alt=\"\" class=\"img-responsive\" style=\"margin: auto;\">\n" +
                    "                <h3 class=\"payment-caption\">Укажите период оплаты</h3>\n" +
                    "                <div class=\"form-group\">\n" +
                    "                    <div class=\"select-wrapper\">\n" +
                    "                        <div class=\"select-arrow-3\"></div>\n" +
                    "                        <div class=\"select-arrow-3\"></div>\n" +
                    "                        <select id=\"period\" class=\"form-control\">\n";
            html += htmlSelectOptions + "\n";
            html += "                        </select>\n" +
                    "                    </div>\n" +
                    "                </div>\n" +
                    "                <div class=\"form-group\" style=\"text-align: center;\">\n" +
                    "                    <input type=\"button\" value=\"перейти к оплате\" class=\"btn btn-success sendbutton\" id=\"checkout\">\n" +
                    "                </div>\n" +
                    "\n" +
                    "            </div>\n" +
                    "        </div>\n" +
                    "    </div>\n" +
                    "    </body>\n" +
                    "    </html>";

            return ResponseEntity.ok(html);
        } catch (BaseException e) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Платёж не найден");
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("ERROR");
        }
    }
}