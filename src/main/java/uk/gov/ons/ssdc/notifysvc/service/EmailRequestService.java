package uk.gov.ons.ssdc.notifysvc.service;

import static uk.gov.ons.ssdc.notifysvc.utils.Constants.QID_TYPE;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_SENSITIVE_PREFIX;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

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
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EnrichedEmailFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveyEmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@Service
public class EmailRequestService {

  @Value("${queueconfig.email-fulfilment-topic}")
  private String emailFulfilmentTopic;

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

  // TODO make this return an optional?
  public UacQidCreatedPayloadDTO fetchNewUacQidPairIfRequired(String[] smsTemplate) {
    if (CollectionUtils.containsAny(
        Arrays.asList(smsTemplate), List.of(TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY))) {
      return uacQidServiceClient.generateUacQid(QID_TYPE);
    }
    return null;
  }

  public boolean isEmailTemplateAllowedOnSurvey(EmailTemplate emailTemplate, Survey survey) {
    return fulfilmentSurveyEmailTemplateRepository.existsByEmailTemplateAndSurvey(
        emailTemplate, survey);
  }

  public boolean validateEmailAddress(String emailAddress) {
    // TODO
    return true;
  }

  public void buildAndSendEnrichedEmailFulfilment(
      UUID caseId,
      String packCode,
      Object uacMetadata,
      UacQidCreatedPayloadDTO newUacQidPair,
      String source,
      String channel,
      UUID correlationId,
      String originatingUser) {
    EnrichedEmailFulfilment enrichedEmailFulfilment = new EnrichedEmailFulfilment();
    enrichedEmailFulfilment.setCaseId(caseId);
    enrichedEmailFulfilment.setPackCode(packCode);
    enrichedEmailFulfilment.setUacMetadata(uacMetadata);

    if (newUacQidPair != null) {
      enrichedEmailFulfilment.setUac(newUacQidPair.getUac());
      enrichedEmailFulfilment.setQid(newUacQidPair.getQid());
    }

    EventDTO enrichedEmailFulfilmentEvent = new EventDTO();

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setTopic(emailFulfilmentTopic);
    eventHeader.setSource(source);
    eventHeader.setChannel(channel);
    eventHeader.setCorrelationId(correlationId);
    eventHeader.setOriginatingUser(originatingUser);
    eventHeader.setDateTime(OffsetDateTime.now(Clock.systemUTC()));
    eventHeader.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setMessageId(UUID.randomUUID());
    enrichedEmailFulfilmentEvent.setHeader(eventHeader);
    enrichedEmailFulfilmentEvent.setPayload(new PayloadDTO());
    enrichedEmailFulfilmentEvent.getPayload().setEnrichedEmailFulfilment(enrichedEmailFulfilment);

    pubSubHelper.publishAndConfirm(emailFulfilmentTopic, enrichedEmailFulfilmentEvent);
  }

  public Map<String, String> buildPersonalisationFromTemplate(
      EmailTemplate emailTemplate, Case caze, String uac, String qid) {
    String[] template = emailTemplate.getTemplate();
    Map<String, String> templateValues = new HashMap<>();

    for (String templateItem : template) {

      if (templateItem.equals(TEMPLATE_UAC_KEY)) {
        templateValues.put(TEMPLATE_UAC_KEY, uac);

      } else if (templateItem.equals(TEMPLATE_QID_KEY)) {
        templateValues.put(TEMPLATE_QID_KEY, qid);

      } else if (templateItem.startsWith(TEMPLATE_SENSITIVE_PREFIX)) {
        templateValues.put(
            templateItem,
            caze.getSampleSensitive()
                .get(templateItem.substring(TEMPLATE_SENSITIVE_PREFIX.length())));

      } else {
        templateValues.put(templateItem, caze.getSample().get(templateItem));
      }
    }

    return templateValues;
  }
}
