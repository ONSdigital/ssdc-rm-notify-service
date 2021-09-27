package uk.gov.ons.ssdc.notifysvc.service;

import static uk.gov.ons.ssdc.notifysvc.utils.Constants.QID_TYPE;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_SENSITIVE_PREFIX;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_UAC_KEY;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EnrichedSmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@Service
public class SmsRequestService {

  @Value("${queueconfig.sms-fulfilment-topic}")
  private String smsFulfilmentTopic;

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

  // TODO make this return an optional?
  public UacQidCreatedPayloadDTO fetchNewUacQidPairIfRequired(String[] smsTemplate) {
    if (CollectionUtils.containsAny(
        Arrays.asList(smsTemplate), List.of(SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY))) {
      return uacQidServiceClient.generateUacQid(QID_TYPE);
    }
    return null;
  }

  public boolean isSmsTemplateAllowedOnSurvey(SmsTemplate smsTemplate, Survey survey) {
    return fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(smsTemplate, survey);
  }

  public boolean validatePhoneNumber(String phoneNumber) {
    // Remove valid leading country code or 0
    String sanitisedPhoneNumber = phoneNumber.replaceFirst("^(0{1,2}44|\\+44|0)", "");

    // The sanitized number must then be 10 digits, starting with 7
    return sanitisedPhoneNumber.length() == 10 && sanitisedPhoneNumber.matches("^7[0-9]+$");
  }

  public void buildAndSendEnrichedSmsFulfilment(
      UUID caseId,
      String packCode,
      UacQidCreatedPayloadDTO newUacQidPair,
      String source,
      String channel,
      UUID correlationId,
      String originatingUser) {
    EnrichedSmsFulfilment enrichedSmsFulfilment = new EnrichedSmsFulfilment();
    enrichedSmsFulfilment.setCaseId(caseId);
    enrichedSmsFulfilment.setPackCode(packCode);

    if (newUacQidPair != null) {
      enrichedSmsFulfilment.setUac(newUacQidPair.getUac());
      enrichedSmsFulfilment.setQid(newUacQidPair.getQid());
    }

    EventDTO enrichedSmsFulfilmentEvent = new EventDTO();

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setTopic(smsFulfilmentTopic);
    eventHeader.setSource(source);
    eventHeader.setChannel(channel);
    eventHeader.setCorrelationId(correlationId);
    eventHeader.setOriginatingUser(originatingUser);
    eventHeader.setDateTime(OffsetDateTime.now(Clock.systemUTC()));
    eventHeader.setVersion(Constants.EVENT_SCHEMA_VERSION);
    eventHeader.setMessageId(UUID.randomUUID());
    enrichedSmsFulfilmentEvent.setHeader(eventHeader);

    enrichedSmsFulfilmentEvent.setPayload(new PayloadDTO());
    enrichedSmsFulfilmentEvent.getPayload().setEnrichedSmsFulfilment(enrichedSmsFulfilment);

    pubSubHelper.publishAndConfirm(smsFulfilmentTopic, enrichedSmsFulfilmentEvent);
  }

  public Map<String, String> buildPersonalisationFromTemplate(
      SmsTemplate smsTemplate, Case caze, String uac, String qid) {
    String[] template = smsTemplate.getTemplate();
    Map<String, String> templateValues = new HashMap<>();

    for (String templateItem : template) {

      if (templateItem.equals(SMS_TEMPLATE_UAC_KEY)) {
        templateValues.put(SMS_TEMPLATE_UAC_KEY, uac);

      } else if (templateItem.equals(SMS_TEMPLATE_QID_KEY)) {
        templateValues.put(SMS_TEMPLATE_QID_KEY, qid);

      } else if (templateItem.startsWith(SMS_TEMPLATE_SENSITIVE_PREFIX)) {
        templateValues.put(
            templateItem,
            caze.getSampleSensitive()
                .get(templateItem.substring(SMS_TEMPLATE_SENSITIVE_PREFIX.length())));

      } else {
        templateValues.put(templateItem, caze.getSample().get(templateItem));
      }
    }

    return templateValues;
  }
}
