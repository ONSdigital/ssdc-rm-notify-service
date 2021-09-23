package uk.gov.ons.ssdc.notifysvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;

@ExtendWith(MockitoExtension.class)
class SmsRequestServiceTest {

  @Mock private FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  @Mock private UacQidServiceClient uacQidServiceClient;

  @InjectMocks private SmsRequestService smsRequestService;

  private static final int QID_TYPE = 1;
  private static final String SMS_TEMPLATE_UAC_KEY = "__uac__";
  private static final String SMS_TEMPLATE_QID_KEY = "__qid__";

  private final String TEST_PACK_CODE = "TEST_PACK_CODE";

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
}
