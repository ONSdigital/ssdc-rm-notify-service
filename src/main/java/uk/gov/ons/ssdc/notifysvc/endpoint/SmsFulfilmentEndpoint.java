package uk.gov.ons.ssdc.notifysvc.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.EnrichedSmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.entity.Case;
import uk.gov.ons.ssdc.notifysvc.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utility.ObjectMapperFactory;
import uk.gov.ons.ssdc.notifysvc.utility.PubSubHelper;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@RestController
@RequestMapping(value = "/sms-fulfilment")
public class SmsFulfilmentEndpoint {

  @Value("${queueconfig.sms-fulfilment-topic}")
  private String smsFulfilmentTopic;

  @Value("${notify.senderId}")
  private String senderId;

  private final CaseRepository caseRepository;
  private final SmsTemplateRepository smsTemplateRepository;
  private final FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  private final UacQidServiceClient uacQidServiceClient;
  private final PubSubHelper pubSubHelper;
  private final NotificationClientApi notificationClientApi;

  private static final Logger logger = LoggerFactory.getLogger(SmsFulfilmentEndpoint.class);
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  private static final int QID_TYPE = 1; // TODO replace hardcoded QID type
  private static final String SMS_TEMPLATE_UAC_KEY = "__uac__";
  private static final String SMS_TEMPLATE_QID_KEY = "__qid__";

  @Autowired
  public SmsFulfilmentEndpoint(
      CaseRepository caseRepository,
      SmsTemplateRepository smsTemplateRepository,
      FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository,
      UacQidServiceClient uacQidServiceClient,
      PubSubHelper pubSubHelper,
      NotificationClientApi notificationClientApi) {
    this.caseRepository = caseRepository;
    this.smsTemplateRepository = smsTemplateRepository;
    this.fulfilmentSurveySmsTemplateRepository = fulfilmentSurveySmsTemplateRepository;
    this.uacQidServiceClient = uacQidServiceClient;
    this.pubSubHelper = pubSubHelper;
    this.notificationClientApi = notificationClientApi;
  }

  @PostMapping
  public ResponseEntity<?> smsFulfilment(
      @RequestBody ResponseManagementEvent responseManagementEvent) throws InterruptedException {
    SmsTemplate smsTemplate = validateSmsFulfilmentEvent(responseManagementEvent);

    UacQidCreatedPayloadDTO newUacQidPair = fetchNewUacQidPairIfRequired(smsTemplate.getTemplate());

    Map<String, String> smsTemplateValues =
        buildTemplateValuesAndPopulateNewUacQidPair(smsTemplate, newUacQidPair);

    ResponseManagementEvent enrichedRME =
        buildEnrichedSmsFulfilmentEvent(responseManagementEvent, newUacQidPair);

    sendEnrichedSmsFulfilmentEvent(enrichedRME);

    sendSmsForFulfilment(
        responseManagementEvent.getPayload().getSmsFulfilment(), smsTemplate, smsTemplateValues);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  private ResponseManagementEvent buildEnrichedSmsFulfilmentEvent(
      ResponseManagementEvent sourceEvent, UacQidCreatedPayloadDTO newUacQidPair) {
    EnrichedSmsFulfilment enrichedSmsFulfilment = new EnrichedSmsFulfilment();
    enrichedSmsFulfilment.setCaseId(sourceEvent.getPayload().getSmsFulfilment().getCaseId());
    enrichedSmsFulfilment.setPackCode(sourceEvent.getPayload().getSmsFulfilment().getPackCode());

    if (newUacQidPair != null) {
      enrichedSmsFulfilment.setUac(newUacQidPair.getUac());
      enrichedSmsFulfilment.setQid(newUacQidPair.getQid());
    }

    ResponseManagementEvent enrichedRME = new ResponseManagementEvent();
    enrichedRME.setEvent(sourceEvent.getEvent());
    enrichedRME.setPayload(new PayloadDTO());
    enrichedRME.getPayload().setEnrichedSmsFulfilment(enrichedSmsFulfilment);
    return enrichedRME;
  }

  public SmsTemplate validateSmsFulfilmentEvent(ResponseManagementEvent responseManagementEvent) {
    SmsFulfilment smsFulfilment = responseManagementEvent.getPayload().getSmsFulfilment();
    Case caze = findCaseById(smsFulfilment.getCaseId());
    SmsTemplate smsTemplate = findSmsTemplateByPackCode(smsFulfilment.getPackCode());
    validateTemplateOnSurvey(smsTemplate, caze.getCollectionExercise().getSurvey());
    validatePhoneNumber(smsFulfilment.getPhoneNumber());
    return smsTemplate;
  }

  public void validatePhoneNumber(String phoneNumber) {
    // String whitespace, full stops, commas, dashes, braces, brackets, and parentheses
    String sanitisedPhoneNumber = phoneNumber.replaceAll("[\\s.,\\-\\[\\]{}()]", "");

    // Remove valid leading country code or 0
    sanitisedPhoneNumber = sanitisedPhoneNumber.replaceFirst("^(0{1,2}44|\\+44|0)", "");

    // The sanitized number must then be 10 digits, starting with 7
    if (sanitisedPhoneNumber.length() != 10 || !sanitisedPhoneNumber.matches("^7[0-9]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid phone number");
    }
  }

  public Map<String, String> buildTemplateValuesAndPopulateNewUacQidPair(
      SmsTemplate smsTemplate, UacQidCreatedPayloadDTO newUacQidPair) {
    String[] template = smsTemplate.getTemplate();
    Map<String, String> templateValues = new HashMap<>();

    for (String templateItem : template) {
      switch (templateItem) {
        case SMS_TEMPLATE_UAC_KEY:
          templateValues.put(SMS_TEMPLATE_UAC_KEY, newUacQidPair.getUac());
          break;
        case SMS_TEMPLATE_QID_KEY:
          templateValues.put(SMS_TEMPLATE_QID_KEY, newUacQidPair.getQid());
          break;
        default:
          throw new NotImplementedException(
              "SMS template item has not been implemented: " + templateItem);
      }
    }
    return templateValues;
  }

  public UacQidCreatedPayloadDTO fetchNewUacQidPairIfRequired(String[] smsTemplate) {
    for (String templateItem : smsTemplate) {
      switch (templateItem) {
        case SMS_TEMPLATE_UAC_KEY:
        case SMS_TEMPLATE_QID_KEY:
          return uacQidServiceClient.generateUacQid(QID_TYPE);
        default:
          throw new NotImplementedException(
              "SMS template item has not been implemented: " + templateItem);
      }
    }
    return null;
  }

  public void sendEnrichedSmsFulfilmentEvent(ResponseManagementEvent enrichedFulfilmentRME)
      throws InterruptedException {
    try {
      // Publish and block until it is complete to ensure the event is not lost
      pubSubHelper.publishAndConfirm(
          smsFulfilmentTopic, objectMapper.writeValueAsBytes(enrichedFulfilmentRME));
    } catch (JsonProcessingException e) {
      logger.error("Error serializing enriched SMS fulfilment to JSON", e);
      throw new RuntimeException("Error serializing enriched SMS fulfilment to JSON", e);
    }
  }

  public void sendSmsForFulfilment(
      SmsFulfilment smsFulfilment, SmsTemplate smsTemplate, Map<String, String> smsTemplateValues) {
    try {
      notificationClientApi.sendSms(
          smsTemplate.getNotifyId().toString(),
          smsFulfilment.getPhoneNumber(),
          smsTemplateValues,
          senderId);
    } catch (NotificationClientException e) {
      logger.error("Error attempting to send SMS with notify client", e);
      throw new RuntimeException("Error attempting to send SMS with notify client", e);
    }
  }

  public void validateTemplateOnSurvey(SmsTemplate template, Survey survey) {
    if (!fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(template, survey)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Template is not allowed on this survey");
    }
  }

  public SmsTemplate findSmsTemplateByPackCode(String packCode) {
    return smsTemplateRepository
        .findById(packCode)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template does not exist"));
  }

  public Case findCaseById(UUID caseId) {
    return caseRepository
        .findById(caseId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case does not exist"));
  }
}
