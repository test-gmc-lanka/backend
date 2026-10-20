package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.ContactRequest;
import com.gmc.backend.service.ContactService;
import com.gmc.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final NotificationService notificationService;

    @Override
    public void sendMessage(ContactRequest request) {
        String phone = request.getPhone() == null || request.getPhone().isBlank()
                ? ""
                : "\nPhone: " + request.getPhone().trim();
        // The sender's email is in the text so admins can reply straight from the email
        String message = "Contact message from " + request.getName().trim()
                + " <" + request.getEmail().trim() + ">" + phone
                + "\nSubject: " + request.getSubject().trim()
                + "\n\n" + request.getMessage().trim();
        notificationService.notifyAdmins(
                message,
                "CONTACT",
                null,
                "Contact form: " + request.getSubject().trim());
    }
}
