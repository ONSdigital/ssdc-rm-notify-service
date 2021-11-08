package uk.gov.ons.ssdc.notifysvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.QID_TYPE;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EnrichedEmailFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveyEmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@ExtendWith(MockitoExtension.class)
class EmailRequestServiceTest {

  @Mock private FulfilmentSurveyEmailTemplateRepository fulfilmentSurveyEmailTemplateRepository;
  @Mock private UacQidServiceClient uacQidServiceClient;
  @Mock private PubSubHelper pubSubHelper;

  @InjectMocks private EmailRequestService emailRequestService;

  @Value("${queueconfig.email-fulfilment-topic}")
  private String emailFulfilmentTopic;

  private final String TEST_PACK_CODE = "TEST_PACK_CODE";
  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";
  private final String TEST_SOURCE = "TEST_SOURCE";
  private final String TEST_CHANNEL = "TEST_CHANNEL";
  private final String TEST_USER = "test@example.test";
  private static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "example@example.com",
        "foo@bar.co.uk",
        "email@subdomain.example.com",
        "email@123.123.123.123",
        "foo+bar@example.com",
        "1234567890@example.com",
      })
  void testValidateEmailAddressValid(String emailAddress) {
    assertTrue(emailRequestService.validateEmailAddress(emailAddress));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1",
        "foo",
        "not@valid",
        "not.valid.com",
        "@.com",
      })
  void testValidateEmailAddressInvalid(String emailAddress) {
    assertFalse(emailRequestService.validateEmailAddress(emailAddress));
  }

  @Test
  void testFetchNewUacQidPairIfRequiredEmptyTemplate() {
    // When
    UacQidCreatedPayloadDTO actualUacQidCreated =
        emailRequestService.fetchNewUacQidPairIfRequired(new String[] {});

    // Then
    assertNull(actualUacQidCreated);
    verifyNoInteractions(uacQidServiceClient);
  }

  @Test
  void testFetchNewUacQidPairIfRequiredUacAndQid() {
    // Given
    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac("TEST_UAC");
    newUacQidCreated.setUac("TEST_QID");
    when(uacQidServiceClient.generateUacQid(QID_TYPE)).thenReturn(newUacQidCreated);

    // When
    UacQidCreatedPayloadDTO actualUacQidCreated =
        emailRequestService.fetchNewUacQidPairIfRequired(
            new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});

    // Then
    assertThat(actualUacQidCreated).isEqualTo(newUacQidCreated);
  }

  @Test
  void testIsEmailTemplateAllowedOnSurvey() {
    // Given
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode(TEST_PACK_CODE);
    when(emailRequestService.isEmailTemplateAllowedOnSurvey(emailTemplate, survey))
        .thenReturn(true);

    // When, then
    assertTrue(emailRequestService.isEmailTemplateAllowedOnSurvey(emailTemplate, survey));
  }

  @Test
  void testIsEmailTemplateAllowedOnSurveyNotAllowed() {
    // Given
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode(TEST_PACK_CODE);
    when(emailRequestService.isEmailTemplateAllowedOnSurvey(emailTemplate, survey))
        .thenReturn(false);

    // When, then
    assertFalse(emailRequestService.isEmailTemplateAllowedOnSurvey(emailTemplate, survey));
  }

  @Test
  void testBuildEnrichedEmailFulfilment() {
    // Given
    UUID caseId = UUID.randomUUID();
    UacQidCreatedPayloadDTO uacQidPair = new UacQidCreatedPayloadDTO();
    uacQidPair.setUac(TEST_UAC);
    uacQidPair.setQid(TEST_QID);
    UUID correlationId = UUID.randomUUID();

    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);

    // When
    emailRequestService.buildAndSendEnrichedEmailFulfilment(
        caseId,
        TEST_PACK_CODE,
        TEST_UAC_METADATA,
        uacQidPair,
        TEST_SOURCE,
        TEST_CHANNEL,
        correlationId,
        TEST_USER);

    // Then
    // Check we're publishing the expected event
    verify(pubSubHelper)
        .publishAndConfirm(eq(emailFulfilmentTopic), eventDTOArgumentCaptor.capture());
    EventDTO enrichedEmailFulfilmentEvent = eventDTOArgumentCaptor.getValue();

    // Check the event header
    EventHeaderDTO enrichedEmailFulfilmentHeader = enrichedEmailFulfilmentEvent.getHeader();
    assertThat(enrichedEmailFulfilmentHeader.getOriginatingUser()).isEqualTo(TEST_USER);
    assertThat(enrichedEmailFulfilmentHeader.getSource()).isEqualTo(TEST_SOURCE);
    assertThat(enrichedEmailFulfilmentHeader.getChannel()).isEqualTo(TEST_CHANNEL);
    assertThat(enrichedEmailFulfilmentHeader.getCorrelationId()).isEqualTo(correlationId);
    assertThat(enrichedEmailFulfilmentHeader.getMessageId()).isNotNull();
    assertThat(enrichedEmailFulfilmentHeader.getTopic()).isEqualTo(emailFulfilmentTopic);
    assertThat(enrichedEmailFulfilmentHeader.getDateTime()).isNotNull();

    // Check the event payload
    EnrichedEmailFulfilment enrichedEmailFulfilment =
        enrichedEmailFulfilmentEvent.getPayload().getEnrichedEmailFulfilment();
    assertThat(enrichedEmailFulfilment.getCaseId()).isEqualTo(caseId);
    assertThat(enrichedEmailFulfilment.getPackCode()).isEqualTo(TEST_PACK_CODE);
    assertThat(enrichedEmailFulfilment.getUac()).isEqualTo(uacQidPair.getUac());
    assertThat(enrichedEmailFulfilment.getQid()).isEqualTo(uacQidPair.getQid());
    assertThat(enrichedEmailFulfilment.getUacMetadata()).isEqualTo(TEST_UAC_METADATA);
  }
}
