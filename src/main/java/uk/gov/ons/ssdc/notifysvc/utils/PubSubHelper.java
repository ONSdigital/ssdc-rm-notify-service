package uk.gov.ons.ssdc.notifysvc.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;
import uk.gov.ons.ssdc.notifysvc.model.dto.EventDTO;

@Component
public class PubSubHelper {
  private final PubSubTemplate pubSubTemplate;

  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();
  private static final Logger logger = LoggerFactory.getLogger(PubSubHelper.class);

  public PubSubHelper(PubSubTemplate pubSubTemplate) {
    this.pubSubTemplate = pubSubTemplate;
  }

  public void publishAndConfirm(String topic, EventDTO payload) throws InterruptedException {
    try {
      pubSubTemplate.publish(topic, objectMapper.writeValueAsBytes(payload)).completable().get();
    } catch (ExecutionException e) {
      logger.error("Error publishing message to PubSub topic " + topic, e);
      throw new RuntimeException("Error publishing message to PubSub topic ", e);
    } catch (JsonProcessingException e) {
      logger.error("Error mapping event to JSON", e);
      throw new RuntimeException("Error mapping event to JSON", e);
    }
  }
}
