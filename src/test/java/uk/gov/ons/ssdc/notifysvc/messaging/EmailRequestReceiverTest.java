package uk.gov.ons.ssdc.notifysvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequest;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.EmailRequestService;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@ExtendWith(MockitoExtension.class)
class EmailRequestReceiverTest {

  @Mock EmailTemplateRepository emailTemplateRepository;
  @Mock CaseRepository caseRepository;
  @Mock EmailRequestService emailRequestService;
  @Mock PubSubHelper pubSubHelper;

  @InjectMocks EmailRequestReceiver emailRequestReceiver;

  @Value("${queueconfig.email-request-enriched-topic}")
  private String emailRequestEnrichedTopic;

  private final String TEST_PACK_CODE = "TEST_PACK_CODE";
  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";
  private final String TEST_REQUEST_NAME = "__request__.name";
  private final Map<String, String> TEST_PERSONALISATION = Map.of("name", "foo");
  private final String VALID_EMAIL_ADDRESS = "example@example.com";
  private static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");

  @Test
  void testReceiveMessageHappyPathWithUacQid() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY, TEST_REQUEST_NAME});

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(caseRepository.existsById(testCase.getId())).thenReturn(true);
    when(emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQidCreated));
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());

    EventDTO emailRequestEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(testCase.getId());
    emailRequest.setPackCode(TEST_PACK_CODE);
    emailRequest.setEmail(VALID_EMAIL_ADDRESS);
    emailRequest.setPersonalisation(TEST_PERSONALISATION);
    emailRequest.setUacMetadata(TEST_UAC_METADATA);
    emailRequestEvent.getPayload().setEmailRequest(emailRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEvent);

    // When
    emailRequestReceiver.receiveMessage(eventMessage);

    // Then
    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper)
        .publishAndConfirm(eq(emailRequestEnrichedTopic), eventDTOArgumentCaptor.capture());
    EventDTO sentEvent = eventDTOArgumentCaptor.getValue();
    assertThat(sentEvent.getHeader().getCorrelationId())
        .isEqualTo(emailRequestEvent.getHeader().getCorrelationId());
    EmailRequestEnriched emailRequestEnriched = sentEvent.getPayload().getEmailRequestEnriched();
    assertThat(emailRequestEnriched.getPackCode()).isEqualTo(emailRequest.getPackCode());
    assertThat(emailRequestEnriched.getCaseId()).isEqualTo(emailRequest.getCaseId());
    assertThat(emailRequestEnriched.getEmail()).isEqualTo(emailRequest.getEmail());
    assertThat(emailRequestEnriched.getUac()).isEqualTo(newUacQidCreated.getUac());
    assertThat(emailRequestEnriched.getQid()).isEqualTo(newUacQidCreated.getQid());

    verify(emailRequestService)
        .buildAndSendEmailConfirmation(
            testCase.getId(),
            emailTemplate.getPackCode(),
            emailRequestEvent.getPayload().getEmailRequest().getUacMetadata(),
            TEST_PERSONALISATION,
            Optional.of(newUacQidCreated),
            false,
            emailRequestEvent.getHeader().getSource(),
            emailRequestEvent.getHeader().getChannel(),
            emailRequestEvent.getHeader().getCorrelationId(),
            emailRequestEvent.getHeader().getOriginatingUser());
  }

  @Test
  void testReceiveMessageHappyPathWithNoPersonalisation() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(caseRepository.existsById(testCase.getId())).thenReturn(true);
    when(emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQidCreated));
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());

    EventDTO emailRequestEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(testCase.getId());
    emailRequest.setPackCode(TEST_PACK_CODE);
    emailRequest.setEmail(VALID_EMAIL_ADDRESS);
    emailRequest.setUacMetadata(TEST_UAC_METADATA);
    emailRequestEvent.getPayload().setEmailRequest(emailRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEvent);

    // When
    emailRequestReceiver.receiveMessage(eventMessage);

    // Then
    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper)
        .publishAndConfirm(eq(emailRequestEnrichedTopic), eventDTOArgumentCaptor.capture());
    EventDTO sentEvent = eventDTOArgumentCaptor.getValue();
    assertThat(sentEvent.getHeader().getCorrelationId())
        .isEqualTo(emailRequestEvent.getHeader().getCorrelationId());
    EmailRequestEnriched emailRequestEnriched = sentEvent.getPayload().getEmailRequestEnriched();
    assertThat(emailRequestEnriched.getPackCode()).isEqualTo(emailRequest.getPackCode());
    assertThat(emailRequestEnriched.getCaseId()).isEqualTo(emailRequest.getCaseId());
    assertThat(emailRequestEnriched.getEmail()).isEqualTo(emailRequest.getEmail());
    assertThat(emailRequestEnriched.getUac()).isEqualTo(newUacQidCreated.getUac());
    assertThat(emailRequestEnriched.getQid()).isEqualTo(newUacQidCreated.getQid());

    verify(emailRequestService)
        .buildAndSendEmailConfirmation(
            testCase.getId(),
            emailTemplate.getPackCode(),
            emailRequestEvent.getPayload().getEmailRequest().getUacMetadata(),
            null,
            Optional.of(newUacQidCreated),
            false,
            emailRequestEvent.getHeader().getSource(),
            emailRequestEvent.getHeader().getChannel(),
            emailRequestEvent.getHeader().getCorrelationId(),
            emailRequestEvent.getHeader().getOriginatingUser());
  }

  @Test
  void testReceiveMessageHappyPathWithoutUacQid() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(new String[] {"foobar"});

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(caseRepository.existsById(testCase.getId())).thenReturn(true);
    when(emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate()))
        .thenReturn(Optional.empty());
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());

    EventDTO emailRequestEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(testCase.getId());
    emailRequest.setPackCode(TEST_PACK_CODE);
    emailRequest.setEmail(VALID_EMAIL_ADDRESS);
    emailRequest.setUacMetadata(TEST_UAC_METADATA);
    emailRequestEvent.getPayload().setEmailRequest(emailRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEvent);

    // When
    emailRequestReceiver.receiveMessage(eventMessage);

    // Then
    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper)
        .publishAndConfirm(eq(emailRequestEnrichedTopic), eventDTOArgumentCaptor.capture());
    EventDTO sentEvent = eventDTOArgumentCaptor.getValue();
    assertThat(sentEvent.getHeader().getCorrelationId())
        .isEqualTo(emailRequestEvent.getHeader().getCorrelationId());
    EmailRequestEnriched emailRequestEnriched = sentEvent.getPayload().getEmailRequestEnriched();
    assertThat(emailRequestEnriched.getPackCode()).isEqualTo(emailRequest.getPackCode());
    assertThat(emailRequestEnriched.getCaseId()).isEqualTo(emailRequest.getCaseId());
    assertThat(emailRequestEnriched.getEmail()).isEqualTo(emailRequest.getEmail());
    assertThat(emailRequestEnriched.getUac()).isNull();
    assertThat(emailRequestEnriched.getQid()).isNull();

    verify(emailRequestService)
        .buildAndSendEmailConfirmation(
            testCase.getId(),
            emailTemplate.getPackCode(),
            emailRequestEvent.getPayload().getEmailRequest().getUacMetadata(),
            null,
            Optional.empty(),
            false,
            emailRequestEvent.getHeader().getSource(),
            emailRequestEvent.getHeader().getChannel(),
            emailRequestEvent.getHeader().getCorrelationId(),
            emailRequestEvent.getHeader().getOriginatingUser());
  }

  @Test
  void testReceiveMessageHappyPathWithPersonalisation() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(new String[] {"foobar", "__request__.spam"});

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(caseRepository.existsById(testCase.getId())).thenReturn(true);
    when(emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate()))
        .thenReturn(Optional.empty());
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());

    EventDTO emailRequestEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(testCase.getId());
    emailRequest.setPackCode(TEST_PACK_CODE);
    emailRequest.setEmail(VALID_EMAIL_ADDRESS);
    emailRequest.setUacMetadata(TEST_UAC_METADATA);
    emailRequest.setPersonalisation(TEST_PERSONALISATION);
    emailRequestEvent.getPayload().setEmailRequest(emailRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEvent);

    // When
    emailRequestReceiver.receiveMessage(eventMessage);

    // Then
    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper)
        .publishAndConfirm(eq(emailRequestEnrichedTopic), eventDTOArgumentCaptor.capture());
    EventDTO sentEvent = eventDTOArgumentCaptor.getValue();
    assertThat(sentEvent.getHeader().getCorrelationId())
        .isEqualTo(emailRequestEvent.getHeader().getCorrelationId());
    EmailRequestEnriched emailRequestEnriched = sentEvent.getPayload().getEmailRequestEnriched();
    assertThat(emailRequestEnriched.getPackCode()).isEqualTo(emailRequest.getPackCode());
    assertThat(emailRequestEnriched.getCaseId()).isEqualTo(emailRequest.getCaseId());
    assertThat(emailRequestEnriched.getEmail()).isEqualTo(emailRequest.getEmail());
    assertThat(emailRequestEnriched.getUac()).isNull();
    assertThat(emailRequestEnriched.getQid()).isNull();

    verify(emailRequestService)
        .buildAndSendEmailConfirmation(
            testCase.getId(),
            emailTemplate.getPackCode(),
            emailRequestEvent.getPayload().getEmailRequest().getUacMetadata(),
            TEST_PERSONALISATION,
            Optional.empty(),
            false,
            emailRequestEvent.getHeader().getSource(),
            emailRequestEvent.getHeader().getChannel(),
            emailRequestEvent.getHeader().getCorrelationId(),
            emailRequestEvent.getHeader().getOriginatingUser());
  }

  @Test
  void testReceiveMessageExceptionOnInvalidEmailAddress() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});

    String invalidEmailAddress = "blah";

    when(emailRequestService.validateEmailAddress(invalidEmailAddress))
        .thenReturn(Optional.of("Email is invalid"));

    EventDTO emailRequestEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(testCase.getId());
    emailRequest.setPackCode(TEST_PACK_CODE);
    emailRequest.setEmail(invalidEmailAddress);
    emailRequestEvent.getPayload().setEmailRequest(emailRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEvent);

    // When, then throws
    Exception thrown =
        assertThrows(
            RuntimeException.class, () -> emailRequestReceiver.receiveMessage(eventMessage));

    assertThat(thrown.getMessage()).containsIgnoringCase("invalid email address");
    verifyNoInteractions(caseRepository);
    verifyNoInteractions(emailTemplateRepository);
    verifyNoInteractions(pubSubHelper);
  }

  @Test
  void testReceiveMessageExceptionOnCaseNotFound() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());
    when(caseRepository.existsById(testCase.getId())).thenReturn(false);

    EventDTO emailRequestEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(testCase.getId());
    emailRequest.setPackCode(TEST_PACK_CODE);
    emailRequest.setEmail(VALID_EMAIL_ADDRESS);
    emailRequestEvent.getPayload().setEmailRequest(emailRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(emailRequestEvent);

    // When, then throws
    Exception thrown =
        assertThrows(
            RuntimeException.class, () -> emailRequestReceiver.receiveMessage(eventMessage));

    assertThat(thrown.getMessage()).containsIgnoringCase("case not found");
    verifyNoInteractions(pubSubHelper);
  }

  @Test
  void testReceiveMessageExceptionOnMissingSMSTemplate() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE");
    emailTemplate.setTemplate(new String[] {TEMPLATE_QID_KEY, TEMPLATE_UAC_KEY});

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());
    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.empty());

    EventDTO smsRequestEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(testCase.getId());
    emailRequest.setPackCode(TEST_PACK_CODE);
    emailRequest.setEmail(VALID_EMAIL_ADDRESS);
    smsRequestEvent.getPayload().setEmailRequest(emailRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When, then throws
    Exception thrown =
        assertThrows(
            RuntimeException.class, () -> emailRequestReceiver.receiveMessage(eventMessage));

    assertThat(thrown.getMessage()).containsIgnoringCase("Email template not found");
    verifyNoInteractions(caseRepository);
    verifyNoInteractions(pubSubHelper);
  }
}
