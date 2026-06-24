package com.heartbeat.ping.controller;

import com.heartbeat.ping.dto.auth.EmailSenderDTO;
import com.heartbeat.ping.dto.auth.EmailSenderResponseDTO;
import com.heartbeat.ping.service.EmailNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class EmailController {
    private EmailNotificationService notificationService;

    public EmailController(EmailNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/sendmail")
    public ResponseEntity<EmailSenderResponseDTO> sendMail(@RequestBody EmailSenderDTO emailSenderDTO) {
        notificationService.sendEmail(emailSenderDTO.getTo(), emailSenderDTO.getSubject(), emailSenderDTO.getMessage());
        return new ResponseEntity<>(new EmailSenderResponseDTO("Email sent successfully!"),HttpStatus.OK );
    }
}
