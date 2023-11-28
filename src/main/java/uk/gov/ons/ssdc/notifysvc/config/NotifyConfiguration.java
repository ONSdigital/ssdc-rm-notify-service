package uk.gov.ons.ssdc.notifysvc.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.service.notify.NotificationClient;

@Getter
@Configuration
@ConfigurationProperties
public class NotifyConfiguration {

  public void setnotify(Map<String, Map<String, String>> notify) {
    this.notify = notify;
  }

  private Map<String, Map<String, String>> notify;

  @Bean
  public Map<String, Map<String, Object>> notificationClientApi() {
    Map<String, Map<String, Object>> notificationClients = new HashMap<>();

    for (String serviceName : notify.keySet()) {
      Map<String, Object> serviceConfig = new HashMap<>();
      serviceConfig.put("sender-id", notify.get(serviceName).get("sender-id"));
      serviceConfig.put(
          "client",
          new NotificationClient(
              notify.get(serviceName).get("api-key"), notify.get(serviceName).get("base-url")));
      notificationClients.put(serviceName, serviceConfig);
    }

    return notificationClients;
  }
}
