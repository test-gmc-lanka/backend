package com.gmc.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import com.gmc.backend.dto.request.ContactRequest;
import com.gmc.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContactServiceImplTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ContactServiceImpl contactService;

    private ContactRequest request(String phone) {
        ContactRequest req = new ContactRequest();
        req.setName(" Nimal Perera ");
        req.setEmail("nimal@example.com");
        req.setPhone(phone);
        req.setSubject("Custom dining table");
        req.setMessage("Can you make a 6-seater teak table?");
        return req;
    }

    @Test
    void sendMessage_notifiesAdminsInAppAndByEmailWithReplyAddress() {
        contactService.sendMessage(request("077 123 4567"));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notifyAdmins(message.capture(), eq("CONTACT"), isNull(), eq("Contact form: Custom dining table"));
        assertThat(message.getValue())
                .contains("Nimal Perera <nimal@example.com>")
                .contains("Phone: 077 123 4567")
                .contains("6-seater teak table");
    }

    @Test
    void sendMessage_withoutPhone_omitsPhoneLine() {
        contactService.sendMessage(request(""));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .notifyAdmins(message.capture(), eq("CONTACT"), isNull(), eq("Contact form: Custom dining table"));
        assertThat(message.getValue()).doesNotContain("Phone:");
    }
}
