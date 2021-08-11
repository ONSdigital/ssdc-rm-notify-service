package uk.gov.ons.ssdc.notifysvc.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.*;
import uk.gov.ons.ssdc.notifysvc.model.entity.Case;
import uk.gov.ons.ssdc.notifysvc.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utility.ObjectMapperFactory;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@RestController
@RequestMapping(value = "/smsfulfilment")
public class SmsFulfilmentEndpoint {

  private final CaseRepository caseRepository;
  private final SmsTemplateRepository smsTemplateRepository;
  private final FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  private final UacQidServiceClient uacQidServiceClient;
  private final PubSubTemplate pubSubTemplate;
  private final NotificationClientApi notificationClientApi;
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  private static final Logger log = LoggerFactory.getLogger(SmsFulfilmentEndpoint.class);

  @Value("${queueconfig.sms-fulfilment-topic}")
  private String smsFulfilmentTopic;

  @Value("${notify.senderId}")
  private String senderId;

  private static final int QID_TYPE = 1; // TODO replace hardcoded QID type
  private static final String PERSONALISATION_UAC_KEY = "uac";
  private static final String PERSONALISATION_QID_KEY = "qid";

  public SmsFulfilmentEndpoint(
      CaseRepository caseRepository,
      SmsTemplateRepository smsTemplateRepository,
      FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository, UacQidServiceClient uacQidServiceClient,
      PubSubTemplate pubSubTemplate,
      NotificationClientApi notificationClientApi) {
    this.caseRepository = caseRepository;
    this.smsTemplateRepository = smsTemplateRepository;
    this.fulfilmentSurveySmsTemplateRepository = fulfilmentSurveySmsTemplateRepository;
    this.uacQidServiceClient = uacQidServiceClient;
    this.pubSubTemplate = pubSubTemplate;
    this.notificationClientApi = notificationClientApi;
  }

  @PostMapping
  public void smsFulfilment(@RequestBody ResponseManagementEvent responseManagementEvent)
      throws InterruptedException {
    SmsTemplate smsTemplate = validateEvent(responseManagementEvent);

    SmsFulfilment smsFulfilment = responseManagementEvent.getPayload().getSmsFulfilment();
    String[] personalisationTemplate = smsTemplate.getTemplate();
    Map<String, String> personalisation = new HashMap<>();

    UacQidCreatedPayloadDTO uacQidCreated = null;
    for (String templateItem : personalisationTemplate) {
      switch (templateItem) {
        case "__uac__":
          if (uacQidCreated == null) {
            uacQidCreated = uacQidServiceClient.generateUacQid(QID_TYPE);
          }
          personalisation.put(PERSONALISATION_UAC_KEY, uacQidCreated.getUac());
          break;
        case "__qid__":
          if (uacQidCreated == null) {
            uacQidCreated = uacQidServiceClient.generateUacQid(QID_TYPE);
          }
          personalisation.put(PERSONALISATION_QID_KEY, uacQidCreated.getQid());
          break;
        default:
          throw new NotImplementedException(
              "SMS template item has not been implemented: " + templateItem);
      }
    }

    ResponseManagementEvent enrichedRME =
        buildEnrichedSmsFulfilmentEvent(responseManagementEvent, personalisation, uacQidCreated);

    try {
      // Publish and block until it is complete to ensure the event is not lost
      pubSubTemplate
          .publish(smsFulfilmentTopic, objectMapper.writeValueAsBytes(enrichedRME))
          .completable()
          .get();
    } catch (ExecutionException e) {
      log.error("Error publishing enriched SMS fulfilment to PubSub topic " + smsFulfilmentTopic, e);
      throw new RuntimeException("Error publishing enriched SMS fulfilment to PubSub", e);
    } catch (JsonProcessingException e) {
      log.error("Error serializing enriched SMS fulfilment to JSON", e);
      throw new RuntimeException("Error serializing enriched SMS fulfilment to JSON", e);
    }

    try {
      notificationClientApi.sendSms(
          smsTemplate.getTemplateId().toString(),
          smsFulfilment.getPhoneNumber(),
          personalisation,
          senderId);
    } catch (NotificationClientException e) {
      log.error("Error attempting to send SMS with notify client", e);
      throw new RuntimeException("Error attempting to send SMS with notify client", e);
    }
  }

  private SmsTemplate validateEvent(ResponseManagementEvent responseManagementEvent) {
    SmsFulfilment smsFulfilment = responseManagementEvent.getPayload().getSmsFulfilment();
    if (responseManagementEvent.getEvent().getType() != EventTypeDTO.SMS_FULFILMENT) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad event type, only accepts " + EventTypeDTO.SMS_FULFILMENT);
    }

    Case caze = caseRepository.findById(smsFulfilment.getCaseId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Case does not exist"));


    SmsTemplate smsTemplate =
        smsTemplateRepository
            .findById(smsFulfilment.getPackCode())
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template does not exist"));

    if (!fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(smsTemplate, caze.getCollectionExercise().getSurvey())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template is not allowed on this survey");
    }
    // TODO validate tel. no
    return smsTemplate;
  }

  private ResponseManagementEvent buildEnrichedSmsFulfilmentEvent(
      ResponseManagementEvent sourceEvent, Map<String, String> personalisation, UacQidCreatedPayloadDTO uacQidCreated) {
    EnrichedSmsFulfilment enrichedSmsFulfilment = new EnrichedSmsFulfilment();
    enrichedSmsFulfilment.setCaseId(sourceEvent.getPayload().getSmsFulfilment().getCaseId());
    enrichedSmsFulfilment.setPackCode(sourceEvent.getPayload().getSmsFulfilment().getPackCode());

    if (uacQidCreated != null) {
      enrichedSmsFulfilment.setUac(personalisation.get(PERSONALISATION_UAC_KEY));
      enrichedSmsFulfilment.setQid(personalisation.get(PERSONALISATION_QID_KEY));
    }

    ResponseManagementEvent enrichedRME = new ResponseManagementEvent();
    enrichedRME.setEvent(sourceEvent.getEvent());
    enrichedRME.setPayload(new PayloadDTO());
    enrichedRME.getPayload().setEnrichedSmsFulfilment(enrichedSmsFulfilment);
    return enrichedRME;
  }
}
