package uk.gov.ons.ssdc.notifysvc.messaging;

import static uk.gov.ons.ssdc.notifysvc.utils.JsonHelper.convertJsonBytesToEvent;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.SmsRequestService;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@MessageEndpoint
public class SmsRequestEnrichedReceiver {

  @Value("${notify.senderId}")
  private String senderId;

  private final SmsTemplateRepository smsTemplateRepository;
  private final CaseRepository caseRepository;
  private final SmsRequestService smsRequestService;
  private final NotificationClientApi notificationClientApi;

  private static final Logger logger = LoggerFactory.getLogger(SmsRequestEnrichedReceiver.class);

  public SmsRequestEnrichedReceiver(
      SmsTemplateRepository smsTemplateRepository,
      CaseRepository caseRepository,
      SmsRequestService smsRequestService,
      NotificationClientApi notificationClientApi) {
    this.smsTemplateRepository = smsTemplateRepository;
    this.caseRepository = caseRepository;
    this.smsRequestService = smsRequestService;
    this.notificationClientApi = notificationClientApi;
  }

  @ServiceActivator(inputChannel = "smsRequestEnrichedInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());
    SmsRequestEnriched smsRequestEnriched = event.getPayload().getSmsRequestEnriched();
    SmsTemplate smsTemplate =
        smsTemplateRepository
            .findById(smsRequestEnriched.getPackCode())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "SMS Template not found: " + smsRequestEnriched.getPackCode()));

    Case caze =
        caseRepository
            .findById(smsRequestEnriched.getCaseId())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Case not found with ID: " + smsRequestEnriched.getCaseId()));

    Map<String, String> personalisationTemplateValues =
        smsRequestService.buildPersonalisationTemplateValues(
            smsTemplate, caze, smsRequestEnriched.getUac(), smsRequestEnriched.getQid());

    try {
      notificationClientApi.sendSms(
          smsTemplate.getNotifyTemplateId().toString(),
          smsRequestEnriched.getPhoneNumber(),
          personalisationTemplateValues,
          senderId);
    } catch (NotificationClientException e) {
      logger.error(
          "Error with Gov Notify when attempting to send SMS (from enriched SMS request event)", e);
      throw new RuntimeException(
          "Error with Gov Notify when attempting to send SMS (from enriched SMS request event)", e);
    }
  }
}
