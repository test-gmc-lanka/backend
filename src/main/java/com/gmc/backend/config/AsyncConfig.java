package com.gmc.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Lets @Async methods (e.g. sending email) run off the request thread
@Configuration
@EnableAsync
public class AsyncConfig {
}
