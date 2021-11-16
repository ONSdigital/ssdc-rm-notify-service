package uk.gov.ons.ssdc.notifysvc.config;

import com.godaddy.logging.LoggingConfigs;
import java.util.TimeZone;
import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
  @PostConstruct
  public void init() {
    LoggingConfigs.setCurrent(LoggingConfigs.getCurrent().useJson());
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }
}
