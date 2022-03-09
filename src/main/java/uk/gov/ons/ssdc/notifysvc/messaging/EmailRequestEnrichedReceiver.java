package uk.gov.ons.ssdc.notifysvc.messaging;

import static uk.gov.ons.ssdc.notifysvc.utils.JsonHelper.convertJsonBytesToEvent;
import static uk.gov.ons.ssdc.notifysvc.utils.PersonalisationTemplateHelper.buildPersonalisationFromTemplate;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@MessageEndpoint
public class EmailRequestEnrichedReceiver {

  @Value("${email-request-enriched-delay}")
  private int emailRequestEnrichedDelay;

  private final EmailTemplateRepository emailTemplateRepository;
  private final CaseRepository caseRepository;
  private final NotificationClientApi notificationClientApi;

  public EmailRequestEnrichedReceiver(
      EmailTemplateRepository emailTemplateRepository,
      CaseRepository caseRepository,
      NotificationClientApi notificationClientApi) {
    this.emailTemplateRepository = emailTemplateRepository;
    this.caseRepository = caseRepository;
    this.notificationClientApi = notificationClientApi;
  }

  @ServiceActivator(inputChannel = "emailRequestEnrichedInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    try {
      Thread.sleep(emailRequestEnrichedDelay);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted during throttling delay", e);
    }

    EventDTO event = convertJsonBytesToEvent(message.getPayload());
    EmailRequestEnriched emailRequestEnriched = event.getPayload().getEmailRequestEnriched();
    EmailTemplate emailTemplate =
        emailTemplateRepository
            .findById(emailRequestEnriched.getPackCode())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Email template not found: " + emailRequestEnriched.getPackCode()));

    Case caze =
        caseRepository
            .findById(emailRequestEnriched.getCaseId())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Case not found with ID: " + emailRequestEnriched.getCaseId()));

    Map<String, String> personalisationTemplateValues =
        buildPersonalisationFromTemplate(
            emailTemplate.getTemplate(),
            caze,
            emailRequestEnriched.getUac(),
            emailRequestEnriched.getQid(),
            emailRequestEnriched.getPersonalisation());

    try {
      notificationClientApi.sendEmail(
          emailTemplate.getNotifyTemplateId().toString(),
          emailRequestEnriched.getEmail(),
          personalisationTemplateValues,
          event.getHeader().getCorrelationId().toString()); // Use the correlation ID as reference
    } catch (NotificationClientException e) {
      throw new RuntimeException(
          "Error with Gov Notify when attempting to send email (from enriched email request event)",
          e);
    }
  }
}
