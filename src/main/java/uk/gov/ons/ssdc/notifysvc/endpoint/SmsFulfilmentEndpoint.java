package uk.gov.ons.ssdc.notifysvc.endpoint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.util.concurrent.ListenableFuture;
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
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;

@RestController
@RequestMapping(value = "/smsfulfilment")
public class SmsFulfilmentEndpoint {

  private final CaseRepository caseRepository;
  private final SmsTemplateRepository smsTemplateRepository;
  private final UacQidServiceClient uacQidServiceClient;
  private final PubSubTemplate pubSubTemplate;
  private final NotificationClient notificationClient;


  private static final int QUESTIONNAIRE_TYPE = 1;

  @Value("${queueconfig.sms-fulfilment-topic}")
  private String smsFulfilmentTopic;

  public SmsFulfilmentEndpoint(
      CaseRepository caseRepository, SmsTemplateRepository smsTemplateRepository, UacQidServiceClient uacQidServiceClient, PubSubTemplate pubSubTemplate) {
    this.caseRepository = caseRepository;
    this.smsTemplateRepository = smsTemplateRepository;
    this.uacQidServiceClient = uacQidServiceClient;
    this.pubSubTemplate = pubSubTemplate;
  }

  @PostMapping
  public void smsFulfilment(@RequestBody ResponseManagementEvent responseManagementEvent) {

    SmsFulfilment smsFulfilment = responseManagementEvent.getPayload().getSmsFulfilment();

    Case caze =
        caseRepository
            .findById(smsFulfilment.getCaseId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));

    SmsTemplate smsTemplate =
        smsTemplateRepository
            .findById(smsFulfilment.getPackCode())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));

    // TODO validate tel. no

    UacQidCreatedPayloadDTO uacQidCreated = uacQidServiceClient.generateUacQid(QUESTIONNAIRE_TYPE);

    EnrichedSmsFulfilment enrichedSmsFulfilment = new EnrichedSmsFulfilment();
    enrichedSmsFulfilment.setCaseId(smsFulfilment.getCaseId());
    enrichedSmsFulfilment.setQid(uacQidCreated.getQid());
    enrichedSmsFulfilment.setUac(uacQidCreated.getUac());

    ResponseManagementEvent enrichedRME = new ResponseManagementEvent();
    enrichedRME.setEvent(responseManagementEvent.getEvent());
    enrichedRME.setPayload(new PayloadDTO());
    enrichedRME.getPayload().setEnrichedSmsFulfilment(enrichedSmsFulfilment);


    // TODO block until confirmed sent
    pubSubTemplate.publish(smsFulfilmentTopic, enrichedRME);


  }


}
