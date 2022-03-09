package uk.gov.ons.ssdc.notifysvc.service;

import static uk.gov.ons.ssdc.notifysvc.utils.Constants.QID_TYPE;
import static uk.gov.ons.ssdc.notifysvc.utils.PersonalisationTemplateHelper.doesTemplateRequireNewUacQid;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
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

  public boolean validateEmailAddress(String emailAddress) {
    Pattern emailPattern =
        Pattern.compile(
            "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])");
    return emailPattern.matcher(emailAddress).find();
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
