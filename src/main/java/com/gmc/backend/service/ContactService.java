package com.gmc.backend.service;

import com.gmc.backend.dto.request.ContactRequest;

public interface ContactService {

    /** Delivers a "Contact us" message to every active admin (in-app notification + email). */
    void sendMessage(ContactRequest request);
}
