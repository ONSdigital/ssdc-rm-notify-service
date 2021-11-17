package uk.gov.ons.ssdc.notifysvc.messaging;

import static java.util.Map.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.constructMessageWithValidTimeStamp;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.EmailRequestService;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@ExtendWith(MockitoExtension.class)
class EmailRequestEnrichedReceiverTest {
  @Mock EmailTemplateRepository emailTemplateRepository;
  @Mock CaseRepository caseRepository;
  @Mock EmailRequestService emailRequestService;
  @Mock NotificationClientApi notificationClientApi;

  @InjectMocks EmailRequestEnrichedReceiver emailRequestEnrichedReceiver;

  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";

  @Value("${queueconfig.email-request-enriched-topic}")
  private String emailRequestEnrichedTopic;

  @Test
  void testReceiveMessageHappyPath() throws NotificationClientException {

    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});
    emailTemplate.setNotifyTemplateId(UUID.randomUUID());

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    EventDTO emailRequestEnrichedEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequestEnriched emailRequestEnriched = new EmailRequestEnriched();
    emailRequestEnriched.setCaseId(testCase.getId());
    emailRequestEnriched.setPackCode("TEST_PACK_CODE");
    emailRequestEnriched.setUac(TEST_UAC);
    emailRequestEnriched.setQid(TEST_QID);
    emailRequestEnriched.setEmail("example@example.com");
    emailRequestEnrichedEvent.getPayload().setEmailRequestEnriched(emailRequestEnriched);

    Map<String, String> personalisationValues =
        Map.ofEntries(entry(TEMPLATE_UAC_KEY, TEST_UAC), entry(TEMPLATE_QID_KEY, TEST_QID));

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEnrichedEvent);

    // When
    emailRequestEnrichedReceiver.receiveMessage(eventMessage);

    // Then
    verify(notificationClientApi)
        .sendEmail(
            emailTemplate.getNotifyTemplateId().toString(),
            emailRequestEnrichedEvent.getPayload().getEmailRequestEnriched().getEmail(),
            personalisationValues,
            emailRequestEnrichedEvent.getHeader().getCorrelationId().toString());
  }
}