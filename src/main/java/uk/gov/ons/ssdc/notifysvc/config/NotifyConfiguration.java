package uk.gov.ons.ssdc.notifysvc.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.ons.ssdc.notifysvc.utils.ObjectMapperFactory;
import uk.gov.service.notify.NotificationClient;

@Configuration
public class NotifyConfiguration {

  @Value("${notifyserviceconfigfile}")
  private String configFile;

  public static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.objectMapper();

  private Map<String, Map<String, String>> initialConfig = null;

  // TODO Rename to something else
  @Bean
  public Map<String, Map<String, Object>> notificationClientApi() {
    try (InputStream configFileStream = new FileInputStream(configFile)) {
      initialConfig = OBJECT_MAPPER.readValue(configFileStream, Map.class);
    } catch (JsonProcessingException | FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Map<String, Map<String, Object>> notificationClients = new HashMap<>();

    for (String serviceName : initialConfig.keySet()) {
      Map<String, Object> serviceConfig = new HashMap<>();
      serviceConfig.put("sender-id", initialConfig.get(serviceName).get("sender-id"));
      serviceConfig.put(
          "client",
          new NotificationClient(
              initialConfig.get(serviceName).get("api-key"),
              initialConfig.get(serviceName).get("base-url")));
      notificationClients.put(serviceName, serviceConfig);
    }

    return notificationClients;
  }
}
