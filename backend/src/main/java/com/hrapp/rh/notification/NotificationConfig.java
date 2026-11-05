package com.hrapp.rh.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées (battement de cœur des flux SSE, purge des tickets). */
@Configuration
@EnableScheduling
public class NotificationConfig {
}
