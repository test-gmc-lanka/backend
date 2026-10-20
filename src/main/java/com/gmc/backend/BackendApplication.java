package com.gmc.backend;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(final String[] args) {
        // All LocalDateTime values (orders, appointments, ...) are Sri Lanka time, whatever
        // zone the server runs in. Override with -Dapp.timezone=... if needed.
        TimeZone.setDefault(TimeZone.getTimeZone(System.getProperty("app.timezone", "Asia/Colombo")));
        SpringApplication.run(BackendApplication.class, args);
    }
}
