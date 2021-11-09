package uk.gov.ons.ssdc.notifysvc.config;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.messaging.MessageChannel;
import uk.gov.ons.ssdc.notifysvc.messaging.ManagedMessageRecoverer;

@Configuration
public class MessageConsumerConfig {
  private final ManagedMessageRecoverer managedMessageRecoverer;
  private final PubSubTemplate pubSubTemplate;

  @Value("${queueconfig.sms-request-subscription}")
  private String smsRequestSubscription;

  @Value("${queueconfig.sms-request-enriched-subscription}")
  private String smsRequestEnrichedSubscription;

  @Value("${queueconfig.email-request-subscription}")
  private String emailRequestSubscription;

  @Value("${queueconfig.email-request-enriched-subscription}")
  private String emailRequestEnrichedSubscription;

  public MessageConsumerConfig(
      ManagedMessageRecoverer managedMessageRecoverer, PubSubTemplate pubSubTemplate) {
    this.managedMessageRecoverer = managedMessageRecoverer;
    this.pubSubTemplate = pubSubTemplate;
  }

  @Bean
  public MessageChannel smsRequestInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel smsRequestEnrichedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel emailRequestInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel emailRequestEnrichedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public PubSubInboundChannelAdapter smsRequestInbound(
      @Qualifier("smsRequestInputChannel") MessageChannel channel) {
    return makeAdapter(channel, smsRequestSubscription);
  }

  @Bean
  public PubSubInboundChannelAdapter smsRequestEnrichedInbound(
      @Qualifier("smsRequestEnrichedInputChannel") MessageChannel channel) {
    return makeAdapter(channel, smsRequestEnrichedSubscription);
  }

  @Bean
  public PubSubInboundChannelAdapter emailRequestInbound(
      @Qualifier("emailRequestInputChannel") MessageChannel channel) {
    return makeAdapter(channel, emailRequestSubscription);
  }

  @Bean
  public PubSubInboundChannelAdapter emailRequestEnrichedInbound(
      @Qualifier("emailRequestEnrichedInputChannel") MessageChannel channel) {
    return makeAdapter(channel, emailRequestEnrichedSubscription);
  }

  private PubSubInboundChannelAdapter makeAdapter(MessageChannel channel, String subscriptionName) {
    PubSubInboundChannelAdapter adapter =
        new PubSubInboundChannelAdapter(pubSubTemplate, subscriptionName);
    adapter.setOutputChannel(channel);
    adapter.setAckMode(AckMode.AUTO);
    return adapter;
  }

  @Bean
  public RequestHandlerRetryAdvice retryAdvice() {
    RequestHandlerRetryAdvice requestHandlerRetryAdvice = new RequestHandlerRetryAdvice();
    requestHandlerRetryAdvice.setRecoveryCallback(managedMessageRecoverer);
    return requestHandlerRetryAdvice;
  }
}
