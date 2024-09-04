package uk.gov.ons.ssdc.notifysvc.messaging;

import static uk.gov.ons.ssdc.notifysvc.utils.JsonHelper.convertJsonBytesToEvent;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(EmailRequestReceiver.class);

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

  private void validateEmailAddress(String emailAddress) {
    Optional<String> validationFailure = emailRequestService.validateEmailAddress(emailAddress);
    if (validationFailure.isPresent()) {
      String responseMessage = String.format("Invalid email address: %s", validationFailure.get());
      throw new RuntimeException(responseMessage);
    }
  }

  @ServiceActivator(inputChannel = "emailRequestInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    long startTime = System.currentTimeMillis();
    EventDTO emailRequestEvent = convertJsonBytesToEvent(message.getPayload());
    EventHeaderDTO emailRequestHeader = emailRequestEvent.getHeader();
    EmailRequest emailRequest = emailRequestEvent.getPayload().getEmailRequest();
    log.atDebug()
        .setMessage("Starting processing email request message")
        .addKeyValue("caseId", emailRequest.getCaseId())
        .addKeyValue("packCode", emailRequest.getPackCode())
        .addKeyValue("messageId", emailRequestHeader.getMessageId())
        .addKeyValue("correlationId", emailRequestHeader.getCorrelationId())
        .log();

    validateEmailAddress(emailRequest.getEmail());

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
    emailRequestService.buildAndSendEmailConfirmation(
        emailRequest.getCaseId(),
        emailRequest.getPackCode(),
        emailRequest.getUacMetadata(),
        emailRequest.getPersonalisation(),
        newUacQidPair,
        emailRequest.isScheduled(),
        emailRequestHeader.getSource(),
        emailRequestHeader.getChannel(),
        emailRequestHeader.getCorrelationId(),
        emailRequestHeader.getOriginatingUser());

    // Send the enriched Email Request, now including the UAC/QID pair if required.
    // This enriched message can then safely be retried multiple times without potentially
    // generating and linking more, unnecessary UAC/QID pairs
    pubSubHelper.publishAndConfirm(emailRequestEnrichedTopic, emailRequestEnrichedEvent);

    log.atDebug()
        .setMessage("Finished processing email request message")
        .addKeyValue("caseId", emailRequest.getCaseId())
        .addKeyValue("packCode", emailRequest.getPackCode())
        .addKeyValue("messageId", emailRequestHeader.getMessageId())
        .addKeyValue("correlationId", emailRequestHeader.getCorrelationId())
        .addKeyValue("processingTimeMillis", System.currentTimeMillis() - startTime)
        .log();
  }

  private EventDTO buildEmailRequestEnrichedEvent(
      EmailRequest emailRequest,
      EventHeaderDTO emailRequestHeader,
      Optional<UacQidCreatedPayloadDTO> uacQidPair) {
    EmailRequestEnriched emailRequestEnriched = new EmailRequestEnriched();
    emailRequestEnriched.setCaseId(emailRequest.getCaseId());
    emailRequestEnriched.setEmail(emailRequest.getEmail());
    emailRequestEnriched.setPackCode(emailRequest.getPackCode());
    emailRequestEnriched.setScheduled(emailRequest.isScheduled());
    emailRequestEnriched.setPersonalisation(emailRequest.getPersonalisation());

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
