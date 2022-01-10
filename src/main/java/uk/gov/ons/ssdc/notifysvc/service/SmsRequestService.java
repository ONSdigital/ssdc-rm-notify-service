package uk.gov.ons.ssdc.notifysvc.service;

import static uk.gov.ons.ssdc.notifysvc.utils.Constants.QID_TYPE;
import static uk.gov.ons.ssdc.notifysvc.utils.PersonalisationTemplateHelper.doesTemplateRequireNewUacQid;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsConfirmation;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@Service
public class SmsRequestService {

  @Value("${queueconfig.sms-confirmation-topic}")
  private String smsConfirmationTopic;

  private final UacQidServiceClient uacQidServiceClient;
  private final FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  private final PubSubHelper pubSubHelper;

  public SmsRequestService(
      UacQidServiceClient uacQidServiceClient,
      FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository,
      PubSubHelper pubSubHelper) {
    this.uacQidServiceClient = uacQidServiceClient;
    this.fulfilmentSurveySmsTemplateRepository = fulfilmentSurveySmsTemplateRepository;
    this.pubSubHelper = pubSubHelper;
  }

  public Optional<UacQidCreatedPayloadDTO> fetchNewUacQidPairIfRequired(String[] smsTemplate) {
    if (doesTemplateRequireNewUacQid(smsTemplate)) {
      return Optional.of(uacQidServiceClient.generateUacQid(QID_TYPE));
    }
    return Optional.empty();
  }

  public boolean isSmsTemplateAllowedOnSurvey(SmsTemplate smsTemplate, Survey survey) {
    return fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(smsTemplate, survey);
  }

  public boolean validatePhoneNumber(String phoneNumber) {
    // Remove valid leading country code or 0
    String sanitisedPhoneNumber = phoneNumber.replaceFirst("^(44|0044|\\+44|0)", "");

    // The sanitized number must then be 10 digits, starting with 7
    return sanitisedPhoneNumber.length() == 10 && sanitisedPhoneNumber.matches("^7[0-9]+$");
  }

  public void buildAndSendEnrichedSmsFulfilment(
      UUID caseId,
      String packCode,
      Object uacMetadata,
      Optional<UacQidCreatedPayloadDTO> newUacQidPair,
      boolean scheduled,
      String source,
      String channel,
      UUID correlationId,
      String originatingUser) {
    SmsConfirmation smsConfirmation = new SmsConfirmation();
    smsConfirmation.setCaseId(caseId);
    smsConfirmation.setPackCode(packCode);
    smsConfirmation.setUacMetadata(uacMetadata);
    smsConfirmation.setScheduled(scheduled);

    if (newUacQidPair.isPresent()) {
      smsConfirmation.setUac(newUacQidPair.get().getUac());
      smsConfirmation.setQid(newUacQidPair.get().getQid());
    }

    EventDTO enrichedSmsFulfilmentEvent = new EventDTO();

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setTopic(smsConfirmationTopic);
    eventHeader.setSource(source);
    eventHeader.setChannel(channel);
    eventHeader.setCorrelationId(correlationId);
    eventHeader.setOriginatingUser(originatingUser);
    eventHeader.setDateTime(OffsetDateTime.now(Clock.systemUTC()));
    eventHeader.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setMessageId(UUID.randomUUID());
    enrichedSmsFulfilmentEvent.setHeader(eventHeader);
    enrichedSmsFulfilmentEvent.setPayload(new PayloadDTO());
    enrichedSmsFulfilmentEvent.getPayload().setSmsConfirmation(smsConfirmation);

    pubSubHelper.publishAndConfirm(smsConfirmationTopic, enrichedSmsFulfilmentEvent);
  }
}
