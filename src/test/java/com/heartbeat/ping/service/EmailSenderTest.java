package com.heartbeat.ping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit test with a mocked {@link JavaMailSender} — never sends real email, and asserts that the
 * sender propagates failures (so the outbox worker can retry) rather than swallowing them.
 */
@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailNotificationService emailNotificationService;

    @Test
    void sendsMessageWithExpectedFields() {
        ReflectionTestUtils.setField(emailNotificationService, "from", "alerts@example.com");
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        emailNotificationService.sendEmail("to@example.com", "subject", "body");

        SimpleMailMessage expected = new SimpleMailMessage();
        expected.setFrom("alerts@example.com");
        expected.setTo("to@example.com");
        expected.setSubject("subject");
        expected.setText("body");
        verify(mailSender).send(expected);
    }

    @Test
    void propagatesSendFailure() {
        ReflectionTestUtils.setField(emailNotificationService, "from", "alerts@example.com");
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
                emailNotificationService.sendEmail("to@example.com", "subject", "body"))
                .isInstanceOf(MailSendException.class);
    }
}
