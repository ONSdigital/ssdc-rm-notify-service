package uk.gov.ons.ssdc.notifysvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientApi;

@Configuration
public class NotifyConfiguration {
  private final String apiKey;
  private final String baseUrl;

  public NotifyConfiguration(
      @Value("${notify.apiKey}") String apiKey, @Value("${notify.baseUrl}") String baseUrl) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
  }

  @Bean
  public NotificationClientApi notificationClientApi() {

    return new NotificationClient(apiKey, baseUrl);
  }
}
