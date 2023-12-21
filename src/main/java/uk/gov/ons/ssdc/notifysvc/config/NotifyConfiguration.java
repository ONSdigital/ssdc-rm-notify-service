package uk.gov.ons.ssdc.notifysvc.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.ons.ssdc.notifysvc.utils.ObjectMapperFactory;

@Configuration
public class NotifyConfiguration {

  @Value("${notifyserviceconfigfile}")
  private String configFile;

  public static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.objectMapper();

  @Bean
  public NotifyServiceRefMapping notifyServiceRefMapping() {
    Map<String, Map<String, String>> rawJsonConfig;

    try (InputStream configFileStream = new FileInputStream(configFile)) {
      rawJsonConfig = OBJECT_MAPPER.readValue(configFileStream, Map.class);
    } catch (JsonProcessingException | FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    NotifyServiceRefMapping notifyServiceRefMapping = new NotifyServiceRefMapping();

    for (String notifyServiceRef : rawJsonConfig.keySet()) {
      notifyServiceRefMapping.addNotifyClient(
          notifyServiceRef,
          rawJsonConfig.get(notifyServiceRef).get("base-url"),
          rawJsonConfig.get(notifyServiceRef).get("api-key"),
          rawJsonConfig.get(notifyServiceRef).get("sender-id"));
    }

    return notifyServiceRefMapping;
  }
}
