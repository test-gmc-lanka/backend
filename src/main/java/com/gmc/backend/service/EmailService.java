package com.gmc.backend.service;

public interface EmailService {

    // Best effort and asynchronous: failures are logged, never thrown to the caller
    void send(String to, String subject, String body);
}
