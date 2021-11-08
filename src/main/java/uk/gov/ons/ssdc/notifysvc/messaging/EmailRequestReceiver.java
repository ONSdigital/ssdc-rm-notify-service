package uk.gov.ons.ssdc.notifysvc.messaging;

import static uk.gov.ons.ssdc.notifysvc.utils.JsonHelper.convertJsonBytesToEvent;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequest;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.EmailRequestService;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@MessageEndpoint
public class EmailRequestReceiver {

  @Value("${queueconfig.email-request-enriched-topic}")
  private String emailRequestEnrichedTopic;

  private final CaseRepository caseRepository;
  private final EmailTemplateRepository emailTemplateRepository;
  private final EmailRequestService emailRequestService;
  private final PubSubHelper pubSubHelper;

  public EmailRequestReceiver(
      CaseRepository caseRepository,
      EmailTemplateRepository emailTemplateRepository,
      EmailRequestService emailRequestService,
      PubSubHelper pubSubHelper) {
    this.caseRepository = caseRepository;
    this.emailTemplateRepository = emailTemplateRepository;
    this.emailRequestService = emailRequestService;
    this.pubSubHelper = pubSubHelper;
  }

  @ServiceActivator(inputChannel = "emailRequestInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO emailRequestEvent = convertJsonBytesToEvent(message.getPayload());
    EventHeaderDTO emailRequestHeader = emailRequestEvent.getHeader();
    EmailRequest emailRequest = emailRequestEvent.getPayload().getEmailRequest();

    if (!emailRequestService.validateEmailAddress(emailRequest.getEmail())) {
      throw new RuntimeException("Invalid email address on email request message");
    }

    EmailTemplate emailTemplate =
        emailTemplateRepository
            .findById(emailRequest.getPackCode())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Email template not found: " + emailRequest.getPackCode()));

    if (!caseRepository.existsById(emailRequest.getCaseId())) {
      throw new RuntimeException("Case not found with ID: " + emailRequest.getCaseId());
    }

    Optional<UacQidCreatedPayloadDTO> newUacQidPair =
        emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate());
    EventDTO emailRequestEnrichedEvent =
        buildEmailRequestEnrichedEvent(emailRequest, emailRequestHeader, newUacQidPair);

    // Send the event, including the UAC/QID pair if required, to be linked and logged
    emailRequestService.buildAndSendEnrichedEmailFulfilment(
        emailRequest.getCaseId(),
        emailRequest.getPackCode(),
        emailRequest.getUacMetadata(),
        newUacQidPair,
        emailRequestHeader.getSource(),
        emailRequestHeader.getChannel(),
        emailRequestHeader.getCorrelationId(),
        emailRequestHeader.getOriginatingUser());

    // Send the enriched Email Request, now including the UAC/QID pair if required.
    // This enriched message can then safely be retried multiple times without potentially
    // generating and linking more, unnecessary UAC/QID pairs
    pubSubHelper.publishAndConfirm(emailRequestEnrichedTopic, emailRequestEnrichedEvent);
  }

  private EventDTO buildEmailRequestEnrichedEvent(
      EmailRequest emailRequest,
      EventHeaderDTO emailRequestHeader,
      Optional<UacQidCreatedPayloadDTO> uacQidPair) {
    EmailRequestEnriched emailRequestEnriched = new EmailRequestEnriched();
    emailRequestEnriched.setCaseId(emailRequest.getCaseId());
    emailRequestEnriched.setEmail(emailRequest.getEmail());
    emailRequestEnriched.setPackCode(emailRequest.getPackCode());

    if (uacQidPair.isPresent()) {
      emailRequestEnriched.setUac(uacQidPair.get().getUac());
      emailRequestEnriched.setQid(uacQidPair.get().getQid());
    }

    EventHeaderDTO enrichedEventHeader = new EventHeaderDTO();
    enrichedEventHeader.setMessageId(UUID.randomUUID());
    enrichedEventHeader.setCorrelationId(emailRequestHeader.getCorrelationId());
    enrichedEventHeader.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    enrichedEventHeader.setChannel(emailRequestHeader.getChannel());
    enrichedEventHeader.setSource(emailRequestHeader.getSource());
    enrichedEventHeader.setOriginatingUser(emailRequestHeader.getOriginatingUser());
    enrichedEventHeader.setTopic(emailRequestEnrichedTopic);
    enrichedEventHeader.setDateTime(OffsetDateTime.now());

    PayloadDTO enrichedPayload = new PayloadDTO();
    enrichedPayload.setEmailRequestEnriched(emailRequestEnriched);

    EventDTO emailRequestEnrichedEvent = new EventDTO();
    emailRequestEnrichedEvent.setHeader(enrichedEventHeader);
    emailRequestEnrichedEvent.setPayload(enrichedPayload);
    return emailRequestEnrichedEvent;
  }
}
