package uk.gov.ons.ssdc.notifysvc.messaging;

import static uk.gov.ons.ssdc.notifysvc.utils.JsonHelper.convertJsonBytesToEvent;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequest;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.SmsRequestService;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@MessageEndpoint
public class SmsRequestReceiver {

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  private final CaseRepository caseRepository;
  private final SmsTemplateRepository smsTemplateRepository;
  private final SmsRequestService smsRequestService;
  private final PubSubHelper pubSubHelper;

  public SmsRequestReceiver(
      CaseRepository caseRepository,
      SmsTemplateRepository smsTemplateRepository,
      SmsRequestService smsRequestService,
      PubSubHelper pubSubHelper) {
    this.caseRepository = caseRepository;
    this.smsTemplateRepository = smsTemplateRepository;
    this.smsRequestService = smsRequestService;
    this.pubSubHelper = pubSubHelper;
  }

  @ServiceActivator(inputChannel = "smsRequestInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());
    EventHeaderDTO smsRequestHeader = event.getHeader();
    SmsRequest smsRequest = event.getPayload().getSmsRequest();

    SmsTemplate smsTemplate =
        smsTemplateRepository
            .findById(smsRequest.getPackCode())
            .orElseThrow(
                () -> new RuntimeException("SMS Template not found: " + smsRequest.getPackCode()));

    caseRepository
        .findById(smsRequest.getCaseId())
        .orElseThrow(
            () -> new RuntimeException("Case not found with ID: " + smsRequest.getCaseId()));

    SmsRequestEnriched smsRequestEnriched = new SmsRequestEnriched();
    smsRequestEnriched.setCaseId(smsRequest.getCaseId());
    smsRequestEnriched.setPhoneNumber(smsRequest.getPhoneNumber());
    smsRequestEnriched.setPackCode(smsRequest.getPackCode());

    UacQidCreatedPayloadDTO newUacQidPair =
        smsRequestService.fetchNewUacQidPairIfRequired(smsTemplate.getTemplate());
    if (newUacQidPair != null) {
      smsRequestEnriched.setUac(newUacQidPair.getUac());
      smsRequestEnriched.setQid(newUacQidPair.getQid());
    }

    EventDTO smsRequestEnrichedEvent = new EventDTO();

    EventHeaderDTO enrichedEventHeader = new EventHeaderDTO();
    enrichedEventHeader.setMessageId(UUID.randomUUID());
    enrichedEventHeader.setCorrelationId(smsRequestHeader.getCorrelationId());
    enrichedEventHeader.setVersion(Constants.EVENT_SCHEMA_VERSION);
    enrichedEventHeader.setChannel(smsRequestHeader.getChannel());
    enrichedEventHeader.setSource(smsRequestHeader.getSource());
    enrichedEventHeader.setOriginatingUser(smsRequestHeader.getOriginatingUser());
    enrichedEventHeader.setTopic(smsRequestEnrichedTopic);
    enrichedEventHeader.setDateTime(OffsetDateTime.now());
    smsRequestEnrichedEvent.setHeader(enrichedEventHeader);

    PayloadDTO enrichedPayload = new PayloadDTO();
    enrichedPayload.setSmsRequestEnriched(smsRequestEnriched);
    smsRequestEnrichedEvent.setPayload(enrichedPayload);

    smsRequestService.buildAndSendEnrichedSmsFulfilment(
        smsRequest.getCaseId(),
        smsRequest.getPackCode(),
        newUacQidPair,
        event.getHeader().getSource(),
        event.getHeader().getChannel(),
        event.getHeader().getCorrelationId(),
        event.getHeader().getOriginatingUser());

    pubSubHelper.publishAndConfirm(smsRequestEnrichedTopic, smsRequestEnrichedEvent);
  }
}
