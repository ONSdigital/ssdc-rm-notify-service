package uk.gov.ons.ssdc.notifysvc.config;

import com.google.api.client.util.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientApi;

@Configuration
public class NotifyConfiguration {

  @Value("${notify.apiKey}")
  private String apiKey;

  @Value("${notify.baseUrl}")
  private String baseUrl;

  @Bean
  public NotificationClientApi notificationClient() {

    return new NotificationClient(apiKey, baseUrl);
  }
}
