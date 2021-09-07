package uk.gov.ons.ssdc.notifysvc.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.EnrichedSmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.RequestDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.RequestHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilmentEmptyResponseSuccess;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilmentResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilmentResponseError;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilmentResponseSuccess;
import uk.gov.ons.ssdc.notifysvc.model.dto.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;
import uk.gov.ons.ssdc.notifysvc.utils.HashHelper;
import uk.gov.ons.ssdc.notifysvc.utils.ObjectMapperFactory;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;
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
  public ResponseEntity<SmsFulfilmentResponse> smsFulfilment(@RequestBody RequestDTO request)
      throws InterruptedException {

    SmsTemplate smsTemplate;
    try {
      smsTemplate = validateRequestAndFetchSmsTemplate(request);
    } catch (ResponseStatusException responseStatusException) {
      return new ResponseEntity<>(
          new SmsFulfilmentResponseError(responseStatusException.getMessage()),
          responseStatusException.getStatus());
    }

    UacQidCreatedPayloadDTO newUacQidPair = fetchNewUacQidPairIfRequired(smsTemplate.getTemplate());

    Map<String, String> smsTemplateValues =
        buildTemplateValuesAndPopulateNewUacQidPair(smsTemplate, newUacQidPair);

    EventDTO enrichedSmsFulfilmentEvent = buildEnrichedSmsFulfilmentEvent(request, newUacQidPair);

    // NOTE: Here we are sending the enriched event BEFORE we make the call to send the SMS.
    // This is to be certain that the record of the UAC link is not lost. If we were to send the SMS
    // first then the event publish failed it would leave the requester with a broken UAC we would
    // be unable to fix
    pubSubHelper.publishAndConfirm(smsFulfilmentTopic, enrichedSmsFulfilmentEvent);

    sendSms(
        request.getPayload().getSmsFulfilment().getPhoneNumber(), smsTemplate, smsTemplateValues);

    return new ResponseEntity<>(sendSmsSuccessResponse(newUacQidPair), HttpStatus.OK);
  }

  private SmsFulfilmentResponse sendSmsSuccessResponse(UacQidCreatedPayloadDTO newUacQidPair) {
    if (newUacQidPair != null) {
      String uacHash = HashHelper.hash(newUacQidPair.getUac());
      return new SmsFulfilmentResponseSuccess(uacHash, newUacQidPair.getQid());
    } else {
      // Send empty, successful response for non-UAC SMS Fulfilments
      return new SmsFulfilmentEmptyResponseSuccess();
    }
  }

  private EventDTO buildEnrichedSmsFulfilmentEvent(
      RequestDTO request, UacQidCreatedPayloadDTO newUacQidPair) {
    EnrichedSmsFulfilment enrichedSmsFulfilment = new EnrichedSmsFulfilment();
    enrichedSmsFulfilment.setCaseId(request.getPayload().getSmsFulfilment().getCaseId());
    enrichedSmsFulfilment.setPackCode(request.getPayload().getSmsFulfilment().getPackCode());

    if (newUacQidPair != null) {
      enrichedSmsFulfilment.setUac(newUacQidPair.getUac());
      enrichedSmsFulfilment.setQid(newUacQidPair.getQid());
    }

    EventDTO enrichedFulfilmentEvent = new EventDTO();

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setTopic(smsFulfilmentTopic);
    eventHeader.setSource(request.getHeader().getSource());
    eventHeader.setChannel(request.getHeader().getChannel());
    eventHeader.setCorrelationId(request.getHeader().getCorrelationId());
    eventHeader.setOriginatingUser(request.getHeader().getOriginatingUser());
    eventHeader.setDateTime(OffsetDateTime.now(Clock.systemUTC()));
    eventHeader.setVersion(Constants.EVENT_SCHEMA_VERSION);
    eventHeader.setMessageId(UUID.randomUUID());
    enrichedFulfilmentEvent.setHeader(eventHeader);

    enrichedFulfilmentEvent.setPayload(new PayloadDTO());
    enrichedFulfilmentEvent.getPayload().setEnrichedSmsFulfilment(enrichedSmsFulfilment);
    return enrichedFulfilmentEvent;
  }

  public SmsTemplate validateRequestAndFetchSmsTemplate(RequestDTO smsFulfilmentRequest) {
    validateRequestHeader(smsFulfilmentRequest.getHeader());
    SmsFulfilment smsFulfilment = smsFulfilmentRequest.getPayload().getSmsFulfilment();
    Case caze = findCaseById(smsFulfilment.getCaseId());
    SmsTemplate smsTemplate = findSmsTemplateByPackCode(smsFulfilment.getPackCode());
    validateTemplateOnSurvey(smsTemplate, caze.getCollectionExercise().getSurvey());
    validatePhoneNumber(smsFulfilment.getPhoneNumber());
    return smsTemplate;
  }

  private void validateRequestHeader(RequestHeaderDTO requestHeader) {
    if (requestHeader.getCorrelationId() == null
        || StringUtils.isBlank(requestHeader.getChannel())
        || StringUtils.isBlank(requestHeader.getSource())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid request header: correlationId, channel and source are mandatory");
    }
  }

  public void validatePhoneNumber(String phoneNumber) {
    // Throws a response status exception if the phone number does not pass validation

    // Remove valid leading country code or 0
    String sanitisedPhoneNumber = phoneNumber.replaceFirst("^(0{1,2}44|\\+44|0)", "");

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
    if (CollectionUtils.containsAny(
        Arrays.asList(smsTemplate), List.of(SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY))) {
      return uacQidServiceClient.generateUacQid(QID_TYPE);
    }
    return null;
  }

  public void sendSms(
      String phoneNumber, SmsTemplate smsTemplate, Map<String, String> smsTemplateValues) {
    try {
      notificationClientApi.sendSms(
          smsTemplate.getNotifyTemplateId().toString(), phoneNumber, smsTemplateValues, senderId);
    } catch (NotificationClientException e) {
      logger.error("Error with Gov Notify when attempting to send SMS", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error with Gov Notify when attempting to send SMS", e);
    }
  }

  public void validateTemplateOnSurvey(SmsTemplate template, Survey survey) {
    if (!fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(template, survey)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The template for this pack code is not allowed on this survey");
    }
  }

  public SmsTemplate findSmsTemplateByPackCode(String packCode) {
    return smsTemplateRepository
        .findById(packCode)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A template does not exist with this pack code"));
  }

  public Case findCaseById(UUID caseId) {
    return caseRepository
        .findById(caseId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The case does not exist"));
  }
}
