package com.example.qldetai.feature.communication.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmailNotificationProperties.class)
public class EmailNotificationConfiguration {}
