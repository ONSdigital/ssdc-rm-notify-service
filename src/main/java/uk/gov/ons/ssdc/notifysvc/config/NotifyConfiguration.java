package uk.gov.ons.ssdc.notifysvc.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientApi;

import java.util.HashMap;
import java.util.Map;

@Getter
@Configuration
@ConfigurationProperties
public class NotifyConfiguration {

  public void setnotify(Map<String,Map<String,String>>  notify) {
    this.notify = notify;
  }

  private Map<String,Map<String,String>> notify;

  @Bean
  public Map<String, NotificationClient> notificationClientApi() {
    Map<String,NotificationClient> notificationClients = new HashMap<>();

    for (String serviceName : notify.keySet()) {
     notificationClients.put(serviceName, new NotificationClient(notify.get(serviceName).get("api-key"),notify.get(serviceName).get("base-url")));
    }

    return notificationClients;
  }
}
