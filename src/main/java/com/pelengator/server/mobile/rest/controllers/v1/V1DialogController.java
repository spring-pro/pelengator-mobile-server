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

import com.pelengator.server.dao.postgresql.dto.DialogMessageMobileEntity;
import com.pelengator.server.dao.postgresql.entity.Dialog;
import com.pelengator.server.dao.postgresql.entity.DialogMessage;
import com.pelengator.server.dao.postgresql.entity.UserPushToken;
import com.pelengator.server.exception.mobile.RequestBodyIsEmptyException;
import com.pelengator.server.mobile.Core;
import com.pelengator.server.mobile.rest.BaseResponse;
import com.pelengator.server.mobile.rest.ErrorResponse;
import com.pelengator.server.mobile.rest.controllers.BaseController;
import com.pelengator.server.mobile.rest.entity.request.v1.chat.DialogSendMessageRequest;
import com.pelengator.server.mobile.rest.entity.request.v1.chat.DialogSetReadRequest;
import com.pelengator.server.utils.ApplicationUtility;
import com.pelengator.server.utils.push.PNChatMessageThread;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.*;

@RestController
@RequestMapping("/v1/chat")
public class V1DialogController extends BaseController {

    private static final Logger LOGGER = Core.getLogger(V1DialogController.class.getSimpleName());

    @RequestMapping(value = "/get/all/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getAllMessages(HttpServletRequest servletRequest,
                                         @PathVariable("token") String token,
                                         @PathVariable("uid") long uid) {

        try {
            Dialog dialog = this.getCore_().getDao().find(Dialog.class, "userId", uid);

            if (dialog == null) {
                dialog = createDialog(uid, true);
            }

            List<DialogMessageMobileEntity> data =
                    this.getCore_().getDialogDao().getAllDialogMessages(dialog.getId());

            if (data.size() == 0) {
                DialogMessage dialogMessage = new DialogMessage();
                dialogMessage.setDialogId(dialog.getId());
                dialogMessage.setSenderType(DialogMessage.SENDER_TYPE_SUPPORT);
                dialogMessage.setSenderId(2L);
                dialogMessage.setMessageType(DialogMessage.MESSAGE_TYPE_DEFAULT);
                dialogMessage.setMessage("Здравствуйте!");
                dialogMessage.setIsRead(false);
                dialogMessage.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                this.getCore_().getDao().save(dialogMessage);

                DialogMessageMobileEntity messageMobileEntity = new DialogMessageMobileEntity();
                messageMobileEntity.setMessageId(dialogMessage.getId());
                messageMobileEntity.setSenderType(dialogMessage.getSenderType());
                messageMobileEntity.setMessageType(dialogMessage.getMessageType());
                messageMobileEntity.setMessageText(dialogMessage.getMessage());
                messageMobileEntity.setIsRead(0);
                messageMobileEntity.setMessageTime(dialogMessage.getCreatedAt().getTime() / 1000);

                data.add(messageMobileEntity);
            }

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/get/{offset}/{limit}/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity getAllMessages(HttpServletRequest servletRequest,
                                         @PathVariable("offset") int offset,
                                         @PathVariable("limit") int limit,
                                         @PathVariable("token") String token,
                                         @PathVariable("uid") long uid) {

        try {
            Dialog dialog = this.getCore_().getDao().find(Dialog.class, "userId", uid);

            if (dialog == null) {
                dialog = createDialog(uid, true);
            }

            List<DialogMessageMobileEntity> data;

            if (limit == 0)
                data = this.getCore_().getDialogDao().getAllDialogMessages(dialog.getId());
            else
                data = this.getCore_().getDialogDao().getDialogMessages(dialog.getId(), offset, limit);

            if (data.size() == 0) {
                DialogMessage dialogMessage = new DialogMessage();
                dialogMessage.setDialogId(dialog.getId());
                dialogMessage.setSenderType(DialogMessage.SENDER_TYPE_SUPPORT);
                dialogMessage.setSenderId(2L);
                dialogMessage.setMessageType(DialogMessage.MESSAGE_TYPE_DEFAULT);
                dialogMessage.setMessage("Здравствуйте!");
                dialogMessage.setIsRead(false);
                dialogMessage.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));
                this.getCore_().getDao().save(dialogMessage);

                DialogMessageMobileEntity messageMobileEntity = new DialogMessageMobileEntity();
                messageMobileEntity.setMessageId(dialogMessage.getId());
                messageMobileEntity.setSenderType(dialogMessage.getSenderType());
                messageMobileEntity.setMessageType(dialogMessage.getMessageType());
                messageMobileEntity.setMessageText(dialogMessage.getMessage());
                messageMobileEntity.setIsRead(0);
                messageMobileEntity.setMessageTime(dialogMessage.getCreatedAt().getTime() / 1000);

                data.add(messageMobileEntity);
            }

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/set/readed/{token}/{uid}",
            method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity setRead(HttpServletRequest servletRequest,
                                  @PathVariable("token") String token,
                                  @PathVariable("uid") long uid,
                                  @RequestBody DialogSetReadRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            if (request.getIds() != null && !request.getIds().isEmpty()) {
                this.getCore_().getDialogDao().setRead(
                        request.getIds().toString().replace("[", "(").replace("]", ")"),
                        null
                );
            }

            this.getCore_().removeUnreadChatMessagesFromCacheL2(uid);

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", null));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    @RequestMapping(value = "/send/{token}/{uid}",
            method = RequestMethod.POST,
            produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity sendMessage(HttpServletRequest servletRequest,
                                      @PathVariable("token") String token,
                                      @PathVariable("uid") long uid,
                                      @RequestBody DialogSendMessageRequest request) {

        try {
            if (request == null)
                throw new RequestBodyIsEmptyException(HttpStatus.OK.value());

            if (StringUtils.isBlank(request.getMessage()) || "null".equals(request.getMessage().toLowerCase()))
                return ResponseEntity.status(HttpStatus.OK).body(
                        new BaseResponse(HttpStatus.OK.value(), "", null));

            Dialog dialog = this.getCore_().getDao().find(Dialog.class, "userId", uid);

            if (dialog == null) {
                dialog = createDialog(uid, false);
            }

            DialogMessage dialogMessage = new DialogMessage();
            dialogMessage.setDialogId(dialog.getId());
            dialogMessage.setSenderType(DialogMessage.SENDER_TYPE_USER);
            dialogMessage.setSenderId(uid);
            dialogMessage.setMessageType(DialogMessage.MESSAGE_TYPE_DEFAULT);
            dialogMessage.setMessage(request.getMessage());
            dialogMessage.setIsRead(false);
            dialogMessage.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));

            dialog.setReadBySupport(false);
            dialog.setUpdatedAt(new Timestamp(new Date().getTime()));

            Session session = this.getCore_().getDao().beginTransaction();
            this.getCore_().getDao().save(dialog, session);
            this.getCore_().getDao().save(dialogMessage, session);
            this.getCore_().getDao().commitTransaction(session);

            // Check if message has been sent between 21h of current day and 09-h of the nex day
            // - send SUPPORT message to the user
            sendDialogRobotMessageAfter21(dialog, uid);

            Map<String, Long> data = new HashMap<>(1);
            data.put("id", dialogMessage.getId());

            return ResponseEntity.status(HttpStatus.OK).body(
                    new BaseResponse(HttpStatus.OK.value(), "", data));
        } catch (Throwable cause) {
            LOGGER.error("REQUEST error -> " + servletRequest.getPathInfo() + ": ", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ErrorResponse(0, cause.getMessage()));
        }
    }

    private void sendDialogRobotMessageAfter21(Dialog dialog, Long userId) {

        try {
            TimeZone timeZone = TimeZone.getTimeZone(ApplicationUtility.GMT_3);
            Calendar currDate21H = Calendar.getInstance(timeZone);
            currDate21H.set(Calendar.HOUR_OF_DAY, 21);
            currDate21H.set(Calendar.MINUTE, 0);
            currDate21H.set(Calendar.SECOND, 0);
            currDate21H.set(Calendar.MILLISECOND, 0);

            Calendar nextDate09H = Calendar.getInstance(timeZone);
            nextDate09H.add(Calendar.DATE, 1);
            nextDate09H.set(Calendar.HOUR_OF_DAY, 9);
            nextDate09H.set(Calendar.MINUTE, 0);
            nextDate09H.set(Calendar.SECOND, 0);
            nextDate09H.set(Calendar.MILLISECOND, 0);

            if (ApplicationUtility.getCurrentTimeStampByGMT(ApplicationUtility.GMT_3) > currDate21H.getTimeInMillis() &&
                    ApplicationUtility.getCurrentTimeStampByGMT(ApplicationUtility.GMT_3) < nextDate09H.getTimeInMillis()) {

                Number dialogMessageCount = this.getCore_().getDialogDao().getDialogMessagesCountBySenderIdAndCreatedAtBetween(
                        dialog.getId(),
                        2L,  // User "Pelengator"
                        new Timestamp(currDate21H.getTimeInMillis()),
                        new Timestamp(nextDate09H.getTimeInMillis()));

                if (dialogMessageCount == null || dialogMessageCount.intValue() == 0) {
                    String messageText = "Спасибо за обращение! Ваше сообщение принято в обработку. Оператор ответит Вам с 9:00 до 21:00 часов. Для экстренной связи воспользуйтесь, пожалуйста, номером технической поддержки 8 800 234-84-43";

                    DialogMessage dialogMessage;
                    dialogMessage = new DialogMessage();
                    dialogMessage.setDialogId(dialog.getId());
                    dialogMessage.setSenderType(DialogMessage.SENDER_TYPE_SUPPORT);
                    dialogMessage.setSenderId(2L); // User "Pelengator"
                    dialogMessage.setMessageType(DialogMessage.MESSAGE_TYPE_SYSTEM);
                    dialogMessage.setMessage(messageText.trim());
                    dialogMessage.setIsRead(false);
                    dialogMessage.setCreatedAt(new Timestamp(ApplicationUtility.getCurrentTimeStampGMT_0()));

                    dialog.setUpdatedAt(new Timestamp(new Date().getTime()));

                    Session session = this.getCore_().getDao().beginTransaction();
                    this.getCore_().getDao().save(dialogMessage, session);
                    this.getCore_().getDao().save(dialog, session);
                    this.getCore_().getDao().commitTransaction(session);

                    DialogMessageMobileEntity dialogMessageMobileEntity = new DialogMessageMobileEntity();
                    dialogMessageMobileEntity.setMessageId(dialogMessage.getId());
                    dialogMessageMobileEntity.setMessageText(dialogMessage.getMessage());
                    dialogMessageMobileEntity.setMessageType(dialogMessage.getMessageType());
                    dialogMessageMobileEntity.setSenderType(dialogMessage.getSenderType());
                    dialogMessageMobileEntity.setIsRead(dialogMessage.isIsRead() ? 1 : 0);
                    dialogMessageMobileEntity.setMessageTime(dialogMessage.getCreatedAt().getTime() / 1000);
                    saveUnreadChatMessageToHazelcast(userId, dialogMessageMobileEntity);

                    UserPushToken userPushToken = this.getCore_().getDao().find(UserPushToken.class, "userId", dialog.getUserId());
                    if (userPushToken != null) {
                        PNChatMessageThread pnChatMessageThread = new PNChatMessageThread(
                                userPushToken.getToken(), userPushToken.getDevice(), dialogMessage.getMessage());

                        pnChatMessageThread.start();
                    }
                }
            }
        } catch (Throwable cause) {
            LOGGER.error("sendDialogRobotMessageAfter21 Error -> ", cause);
        }
    }
}