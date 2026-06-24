package com.heartbeat.ping;

import com.heartbeat.ping.config.properties.AlertProperties;
import com.heartbeat.ping.config.properties.EmailProperties;
import com.heartbeat.ping.config.properties.HttpClientProperties;
import com.heartbeat.ping.config.properties.RetentionProperties;
import com.heartbeat.ping.config.properties.SchedulerProperties;
import com.heartbeat.ping.config.properties.SsrfProperties;
import com.heartbeat.ping.config.properties.UptimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableConfigurationProperties({
        SchedulerProperties.class,
        HttpClientProperties.class,
        AlertProperties.class,
        EmailProperties.class,
        RetentionProperties.class,
        SsrfProperties.class,
        UptimeProperties.class
})
public class PingApplication {

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

}
