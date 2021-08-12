package uk.gov.ons.ssdc.notifysvc.utility;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.concurrent.ExecutionException;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.stereotype.Component;

@Component
public class PubSubHelper {
  private final PubSubTemplate pubSubTemplate;

  private static final Logger logger = LoggerFactory.getLogger(PubSubHelper.class);

  public PubSubHelper(PubSubTemplate pubSubTemplate) {
    this.pubSubTemplate = pubSubTemplate;
  }

  public void publishAndConfirm(String topic, byte[] payload) throws InterruptedException {
    try {
      pubSubTemplate.publish(topic, payload).completable().get();
    } catch (ExecutionException e) {
      logger.error("Error publishing message to PubSub topic " + topic, e);
      throw new RuntimeException("Error publishing message to PubSub topic ", e);
    }
  }
}
