package uk.gov.ons.ssdc.notifysvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientApi;

@Configuration
public class NotifyConfiguration {
  @Value("${notify.apikey}")
  private String apiKey;

  @Value("${notify.baseurl}")
  private String baseUrl;

  @Bean
  public NotificationClientApi notificationClientApi() {

    return new NotificationClient(apiKey, baseUrl);
  }
}
