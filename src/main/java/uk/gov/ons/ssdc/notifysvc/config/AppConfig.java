package uk.gov.ons.ssdc.notifysvc.config;

import com.godaddy.logging.LoggingConfigs;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@Configuration
public class AppConfig {
  @PostConstruct
  public void init() {
    LoggingConfigs.setCurrent(LoggingConfigs.getCurrent().useJson());
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

}
