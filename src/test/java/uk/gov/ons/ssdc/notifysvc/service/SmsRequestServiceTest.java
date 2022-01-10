package uk.gov.ons.ssdc.notifysvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.QID_TYPE;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import java.util.Map;
import java.util.Optional;
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
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsConfirmation;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@ExtendWith(MockitoExtension.class)
class SmsRequestServiceTest {

  @Mock private FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  @Mock private UacQidServiceClient uacQidServiceClient;
  @Mock private PubSubHelper pubSubHelper;

  @InjectMocks private SmsRequestService smsRequestService;

  @Value("${queueconfig.sms-fulfilment-topic}")
  private String smsFulfilmentTopic;

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
        "07123456789",
        "07876543456",
        "+447123456789",
        "00447123456789",
        "447123456789",
        "7123456789",
      })
  void testValidatePhoneNumberValid(String phoneNumber) {
    assertTrue(smsRequestService.validatePhoneNumber(phoneNumber));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1",
        "foo",
        "007",
        "071234567890",
        "0447123456789",
        "000447123456789",
        "+44 7123456789",
        "44+7123456789",
        "0712345678a",
        "@7123456789",
        "07123 456789",
        "(+44) 07123456789"
      })
  void testValidatePhoneNumberInvalid(String phoneNumber) {
    assertFalse(smsRequestService.validatePhoneNumber(phoneNumber));
  }

  @Test
  void testFetchNewUacQidPairIfRequiredEmptyTemplate() {
    // When
    Optional<UacQidCreatedPayloadDTO> actualUacQidCreated =
        smsRequestService.fetchNewUacQidPairIfRequired(new String[] {});

    // Then
    assertThat(actualUacQidCreated).isEmpty();
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
    Optional<UacQidCreatedPayloadDTO> actualUacQidCreated =
        smsRequestService.fetchNewUacQidPairIfRequired(
            new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});

    // Then
    assertThat(actualUacQidCreated).contains(newUacQidCreated);
  }

  @Test
  void testIsSmsTemplateAllowedOnSurvey() {
    // Given
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode(TEST_PACK_CODE);
    when(smsRequestService.isSmsTemplateAllowedOnSurvey(smsTemplate, survey)).thenReturn(true);

    // When, then
    assertTrue(smsRequestService.isSmsTemplateAllowedOnSurvey(smsTemplate, survey));
  }

  @Test
  void testIsSmsTemplateAllowedOnSurveyNotAllowed() {
    // Given
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode(TEST_PACK_CODE);
    when(smsRequestService.isSmsTemplateAllowedOnSurvey(smsTemplate, survey)).thenReturn(false);

    // When, then
    assertFalse(smsRequestService.isSmsTemplateAllowedOnSurvey(smsTemplate, survey));
  }

  @Test
  void testBuildEnrichedSmsFulfilment() {
    // Given
    UUID caseId = UUID.randomUUID();
    UacQidCreatedPayloadDTO uacQidPair = new UacQidCreatedPayloadDTO();
    uacQidPair.setUac(TEST_UAC);
    uacQidPair.setQid(TEST_QID);
    UUID correlationId = UUID.randomUUID();

    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);

    // When
    smsRequestService.buildAndSendEnrichedSmsFulfilment(
        caseId,
        TEST_PACK_CODE,
        TEST_UAC_METADATA,
        Optional.of(uacQidPair),
        true,
        TEST_SOURCE,
        TEST_CHANNEL,
        correlationId,
        TEST_USER);

    // Then
    // Check we're publishing the expected event
    verify(pubSubHelper)
        .publishAndConfirm(eq(smsFulfilmentTopic), eventDTOArgumentCaptor.capture());
    EventDTO enrichedSmsFulfilmentEvent = eventDTOArgumentCaptor.getValue();

    // Check the event header
    EventHeaderDTO enrichedSmsFulfilmentHeader = enrichedSmsFulfilmentEvent.getHeader();
    assertThat(enrichedSmsFulfilmentHeader.getOriginatingUser()).isEqualTo(TEST_USER);
    assertThat(enrichedSmsFulfilmentHeader.getSource()).isEqualTo(TEST_SOURCE);
    assertThat(enrichedSmsFulfilmentHeader.getChannel()).isEqualTo(TEST_CHANNEL);
    assertThat(enrichedSmsFulfilmentHeader.getCorrelationId()).isEqualTo(correlationId);
    assertThat(enrichedSmsFulfilmentHeader.getMessageId()).isNotNull();
    assertThat(enrichedSmsFulfilmentHeader.getTopic()).isEqualTo(smsFulfilmentTopic);
    assertThat(enrichedSmsFulfilmentHeader.getDateTime()).isNotNull();

    // Check the event payload
    SmsConfirmation smsConfirmation = enrichedSmsFulfilmentEvent.getPayload().getSmsConfirmation();
    assertThat(smsConfirmation.getCaseId()).isEqualTo(caseId);
    assertThat(smsConfirmation.getPackCode()).isEqualTo(TEST_PACK_CODE);
    assertThat(smsConfirmation.getUac()).isEqualTo(uacQidPair.getUac());
    assertThat(smsConfirmation.getQid()).isEqualTo(uacQidPair.getQid());
    assertThat(smsConfirmation.getUacMetadata()).isEqualTo(TEST_UAC_METADATA);
  }
}
