package uk.gov.ons.ssdc.notifysvc.service;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.QID_TYPE;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_SENSITIVE_PREFIX;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_UAC_KEY;

import java.util.HashMap;
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
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EnrichedSmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "07123456789",
        "07876543456",
        "+447123456789",
        "0447123456789",
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
    UacQidCreatedPayloadDTO actualUacQidCreated =
        smsRequestService.fetchNewUacQidPairIfRequired(new String[] {});

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
        smsRequestService.fetchNewUacQidPairIfRequired(
            new String[] {SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY});

    // Then
    assertThat(actualUacQidCreated).isEqualTo(newUacQidCreated);
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

    Map<String, String> testUacMetadata = new HashMap<>();
    testUacMetadata.put("Wave of Contact", "1");

    // When
    smsRequestService.buildAndSendEnrichedSmsFulfilment(
        caseId,
        TEST_PACK_CODE,
        testUacMetadata,
        uacQidPair,
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
    EnrichedSmsFulfilment enrichedSmsFulfilment =
        enrichedSmsFulfilmentEvent.getPayload().getEnrichedSmsFulfilment();
    assertThat(enrichedSmsFulfilment.getCaseId()).isEqualTo(caseId);
    assertThat(enrichedSmsFulfilment.getPackCode()).isEqualTo(TEST_PACK_CODE);
    assertThat(enrichedSmsFulfilment.getUac()).isEqualTo(uacQidPair.getUac());
    assertThat(enrichedSmsFulfilment.getQid()).isEqualTo(uacQidPair.getQid());
  }

  @Test
  void testBuildPersonalisationFromTemplate() {
    // Given
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setTemplate(
        new String[] {
          SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY, "foo", SMS_TEMPLATE_SENSITIVE_PREFIX + "foo"
        });

    Case testCase = new Case();
    testCase.setSample(Map.ofEntries(entry("foo", "bar")));
    testCase.setSampleSensitive(Map.ofEntries(entry("foo", "secretBar")));

    // When
    Map<String, String> personalisationValues =
        smsRequestService.buildPersonalisationFromTemplate(
            smsTemplate, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues)
        .containsEntry(SMS_TEMPLATE_UAC_KEY, TEST_UAC)
        .containsEntry(SMS_TEMPLATE_QID_KEY, TEST_QID)
        .containsEntry("foo", "bar")
        .containsEntry(SMS_TEMPLATE_SENSITIVE_PREFIX + "foo", "secretBar");
  }

  @Test
  void testBuildPersonalisationFromTemplateJustUac() {
    // Given
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setTemplate(new String[] {SMS_TEMPLATE_UAC_KEY});

    Case testCase = new Case();

    // When
    Map<String, String> personalisationValues =
        smsRequestService.buildPersonalisationFromTemplate(
            smsTemplate, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues)
        .containsEntry(SMS_TEMPLATE_UAC_KEY, TEST_UAC)
        .containsOnlyKeys(SMS_TEMPLATE_UAC_KEY);
  }

  @Test
  void testBuildPersonalisationFromTemplateJustQid() {
    // Given
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setTemplate(new String[] {SMS_TEMPLATE_QID_KEY});

    Case testCase = new Case();

    // When
    Map<String, String> personalisationValues =
        smsRequestService.buildPersonalisationFromTemplate(
            smsTemplate, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues)
        .containsEntry(SMS_TEMPLATE_QID_KEY, TEST_QID)
        .containsOnlyKeys(SMS_TEMPLATE_QID_KEY);
  }

  @Test
  void testBuildPersonalisationFromTemplateJustSampleFields() {
    // Given
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setTemplate(new String[] {"foo", "spam"});

    Case testCase = new Case();
    testCase.setSample(Map.ofEntries(entry("foo", "bar"), entry("spam", "eggs")));

    // When
    Map<String, String> personalisationValues =
        smsRequestService.buildPersonalisationFromTemplate(
            smsTemplate, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues).containsEntry("foo", "bar").containsEntry("spam", "eggs");
  }

  @Test
  void testBuildPersonalisationFromTemplateJustSampleSensitiveFields() {
    // Given
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setTemplate(
        new String[] {
          SMS_TEMPLATE_SENSITIVE_PREFIX + "foo", SMS_TEMPLATE_SENSITIVE_PREFIX + "spam"
        });

    Case testCase = new Case();
    testCase.setSampleSensitive(
        Map.ofEntries(entry("foo", "secretBar"), entry("spam", "secretEggs")));

    // When
    Map<String, String> personalisationValues =
        smsRequestService.buildPersonalisationFromTemplate(
            smsTemplate, testCase, TEST_UAC, TEST_QID);

    // Then
    assertThat(personalisationValues)
        .containsEntry(SMS_TEMPLATE_SENSITIVE_PREFIX + "foo", "secretBar")
        .containsEntry(SMS_TEMPLATE_SENSITIVE_PREFIX + "spam", "secretEggs");
  }
}
