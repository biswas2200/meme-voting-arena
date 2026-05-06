package com.meme.gdg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task execution for the application.
 * Used by {@link com.meme.gdg.scheduler.RoundAdvancementScheduler}.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
