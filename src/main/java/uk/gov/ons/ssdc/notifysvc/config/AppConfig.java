package uk.gov.ons.ssdc.notifysvc.config;

import com.godaddy.logging.LoggingConfigs;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
  @Value("${logging.profile}")
  private String loggingProfile;

  @PostConstruct
  public void init() {
    if ("STRUCTURED".equals(loggingProfile)) {
      LoggingConfigs.setCurrent(LoggingConfigs.getCurrent().useJson());
    }

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }
}
