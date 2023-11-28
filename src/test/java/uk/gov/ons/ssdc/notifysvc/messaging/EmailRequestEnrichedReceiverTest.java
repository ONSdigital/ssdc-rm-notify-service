package uk.gov.ons.ssdc.notifysvc.messaging;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.constructMessageWithValidTimeStamp;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_REQUEST_PREFIX;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import java.util.HashMap;
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
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

@ExtendWith(MockitoExtension.class)
class EmailRequestEnrichedReceiverTest {
  @Mock EmailTemplateRepository emailTemplateRepository;
  @Mock CaseRepository caseRepository;
  @Mock EmailRequestService emailRequestService;
  @Mock Map<String, Map<String, Object>> notificationClientApi;
  @Mock NotificationClient notificationClient;

  @InjectMocks EmailRequestEnrichedReceiver emailRequestEnrichedReceiver;

  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";
  private final Map<String, String> TEST_PERSONALISATION = Map.of("foo", "bar");

  @Value("${queueconfig.email-request-enriched-topic}")
  private String emailRequestEnrichedTopic;

  @Test
  void testReceiveMessageHappyPath() throws NotificationClientException {

    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(
        new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY, TEMPLATE_REQUEST_PREFIX + "foo"});
    emailTemplate.setNotifyTemplateId(UUID.randomUUID());
    emailTemplate.setNotifyServiceRef("test-service");

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    EventDTO emailRequestEnrichedEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequestEnriched emailRequestEnriched = new EmailRequestEnriched();
    emailRequestEnriched.setCaseId(testCase.getId());
    emailRequestEnriched.setPackCode("TEST_PACK_CODE");
    emailRequestEnriched.setUac(TEST_UAC);
    emailRequestEnriched.setQid(TEST_QID);
    emailRequestEnriched.setPersonalisation(TEST_PERSONALISATION);
    emailRequestEnriched.setEmail("example@example.com");
    emailRequestEnrichedEvent.getPayload().setEmailRequestEnriched(emailRequestEnriched);

    Map<String, Object> notifyConfig = new HashMap<>();
    notifyConfig.put("sender-id", "senderid1234");
    notifyConfig.put("client", notificationClient);

    Map<String, String> personalisationValues =
        Map.ofEntries(
            entry(TEMPLATE_UAC_KEY, TEST_UAC),
            entry(TEMPLATE_QID_KEY, TEST_QID),
            entry(TEMPLATE_REQUEST_PREFIX + "foo", "bar"));

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(notificationClientApi.get("test-service")).thenReturn(notifyConfig);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEnrichedEvent);

    // When
    emailRequestEnrichedReceiver.receiveMessage(eventMessage);

    // Then
    verify(notificationClient)
        .sendEmail(
            emailTemplate.getNotifyTemplateId().toString(),
            emailRequestEnrichedEvent.getPayload().getEmailRequestEnriched().getEmail(),
            personalisationValues,
            emailRequestEnrichedEvent.getHeader().getCorrelationId().toString());
  }

  @Test
  void testReceiveMessageNoPersonalisationSupplied() throws NotificationClientException {

    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(
        new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY, TEMPLATE_REQUEST_PREFIX + "foo"});
    emailTemplate.setNotifyTemplateId(UUID.randomUUID());
    emailTemplate.setNotifyServiceRef("test-service");

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

    Map<String, Object> notifyConfig = new HashMap<>();
    notifyConfig.put("sender-id", "senderid1234");
    notifyConfig.put("client", notificationClient);

    Map<String, String> personalisationValues =
        Map.ofEntries(entry(TEMPLATE_UAC_KEY, TEST_UAC), entry(TEMPLATE_QID_KEY, TEST_QID));

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(notificationClientApi.get("test-service")).thenReturn(notifyConfig);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEnrichedEvent);

    // When
    emailRequestEnrichedReceiver.receiveMessage(eventMessage);

    // Then
    verify(notificationClient)
        .sendEmail(
            emailTemplate.getNotifyTemplateId().toString(),
            emailRequestEnrichedEvent.getPayload().getEmailRequestEnriched().getEmail(),
            personalisationValues,
            emailRequestEnrichedEvent.getHeader().getCorrelationId().toString());
  }

  @Test
  void testReceiveMessageSendException() throws NotificationClientException {

    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});
    emailTemplate.setNotifyTemplateId(UUID.randomUUID());
    emailTemplate.setNotifyServiceRef("test-service");

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
    Map<String, Object> notifyConfig = new HashMap<>();
    notifyConfig.put("sender-id", "senderid1234");
    notifyConfig.put("client", notificationClient);

    Map<String, String> personalisationValues =
        Map.ofEntries(entry(TEMPLATE_UAC_KEY, TEST_UAC), entry(TEMPLATE_QID_KEY, TEST_QID));

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(notificationClientApi.get("test-service")).thenReturn(notifyConfig);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEnrichedEvent);

    when(notificationClient.sendEmail(any(), any(), any(), any()))
        .thenThrow(new NotificationClientException("Test Throw"));

    // When
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> emailRequestEnrichedReceiver.receiveMessage(eventMessage));
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Error with Gov Notify when attempting to send email (from enriched email request event)");
  }

  @Test
  public void testEmailTemplateNotFoundException() {
    // Given
    EventDTO emailRequestEnrichedEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequestEnriched emailRequestEnriched = new EmailRequestEnriched();
    emailRequestEnriched.setPackCode("TEST_PACK_CODE");
    emailRequestEnrichedEvent.getPayload().setEmailRequestEnriched(emailRequestEnriched);

    when(emailTemplateRepository.findById(any())).thenReturn(Optional.empty());

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEnrichedEvent);

    // When
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> emailRequestEnrichedReceiver.receiveMessage(eventMessage));
    assertThat(thrown.getMessage()).isEqualTo("Email template not found: TEST_PACK_CODE");
  }

  @Test
  public void testCaseNotFoundException() {
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
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.empty());

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEnrichedEvent);

    // When
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> emailRequestEnrichedReceiver.receiveMessage(eventMessage));
    assertThat(thrown.getMessage()).isEqualTo("Case not found with ID: " + testCase.getId());
  }
}
