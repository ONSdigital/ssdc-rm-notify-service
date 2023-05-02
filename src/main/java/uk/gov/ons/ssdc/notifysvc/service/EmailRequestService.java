package uk.gov.ons.ssdc.notifysvc.service;

import static uk.gov.ons.ssdc.notifysvc.utils.Constants.QID_TYPE;
import static uk.gov.ons.ssdc.notifysvc.utils.PersonalisationTemplateHelper.doesTemplateRequireNewUacQid;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.EmailRule;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailConfirmation;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveyEmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@Service
public class EmailRequestService {

  @Value("${queueconfig.email-confirmation-topic}")
  private String emailConfirmationTopic;

  private final UacQidServiceClient uacQidServiceClient;
  private final FulfilmentSurveyEmailTemplateRepository fulfilmentSurveyEmailTemplateRepository;
  private final PubSubHelper pubSubHelper;

  private final EmailRule emailValidationRule = new EmailRule(true);

  public EmailRequestService(
      UacQidServiceClient uacQidServiceClient,
      FulfilmentSurveyEmailTemplateRepository fulfilmentSurveyEmailTemplateRepository,
      PubSubHelper pubSubHelper) {
    this.uacQidServiceClient = uacQidServiceClient;
    this.fulfilmentSurveyEmailTemplateRepository = fulfilmentSurveyEmailTemplateRepository;
    this.pubSubHelper = pubSubHelper;
  }

  public Optional<UacQidCreatedPayloadDTO> fetchNewUacQidPairIfRequired(String[] emailTemplate) {
    if (doesTemplateRequireNewUacQid(emailTemplate)) {
      return Optional.of(uacQidServiceClient.generateUacQid(QID_TYPE));
    }
    return Optional.empty();
  }

  public boolean isEmailTemplateAllowedOnSurvey(EmailTemplate emailTemplate, Survey survey) {
    return fulfilmentSurveyEmailTemplateRepository.existsByEmailTemplateAndSurvey(
        emailTemplate, survey);
  }

  public Optional<String> validateEmailAddress(String emailAddress) {
    return emailValidationRule.checkValidity(emailAddress);
  }

  public void buildAndSendEmailConfirmation(
      UUID caseId,
      String packCode,
      Object uacMetadata,
      Map<String, String> personalisation,
      Optional<UacQidCreatedPayloadDTO> newUacQidPair,
      boolean scheduled,
      String source,
      String channel,
      UUID correlationId,
      String originatingUser) {
    EmailConfirmation emailConfirmation = new EmailConfirmation();
    emailConfirmation.setCaseId(caseId);
    emailConfirmation.setPackCode(packCode);
    emailConfirmation.setUacMetadata(uacMetadata);
    emailConfirmation.setScheduled(scheduled);
    emailConfirmation.setPersonalisation(personalisation);

    if (newUacQidPair.isPresent()) {
      emailConfirmation.setUac(newUacQidPair.get().getUac());
      emailConfirmation.setQid(newUacQidPair.get().getQid());
    }

    EventDTO enrichedEmailFulfilmentEvent = new EventDTO();

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setTopic(emailConfirmationTopic);
    eventHeader.setSource(source);
    eventHeader.setChannel(channel);
    eventHeader.setCorrelationId(correlationId);
    eventHeader.setOriginatingUser(originatingUser);
    eventHeader.setDateTime(OffsetDateTime.now(Clock.systemUTC()));
    eventHeader.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setMessageId(UUID.randomUUID());
    enrichedEmailFulfilmentEvent.setHeader(eventHeader);
    enrichedEmailFulfilmentEvent.setPayload(new PayloadDTO());
    enrichedEmailFulfilmentEvent.getPayload().setEmailConfirmation(emailConfirmation);

    pubSubHelper.publishAndConfirm(emailConfirmationTopic, enrichedEmailFulfilmentEvent);
  }
}
