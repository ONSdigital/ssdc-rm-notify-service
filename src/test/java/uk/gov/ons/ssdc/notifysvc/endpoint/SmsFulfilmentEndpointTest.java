package uk.gov.ons.ssdc.notifysvc.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.SmsRequestService;
import uk.gov.ons.ssdc.notifysvc.utils.HashHelper;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@ExtendWith(MockitoExtension.class)
class SmsFulfilmentEndpointTest {

  @Value("${notify.senderId}")
  private String senderId;

  private static final String SMS_FULFILMENT_ENDPOINT = "/sms-fulfilment";

  private static final String VALID_PHONE_NUMBER = "07123456789";
  private static final String TEST_SOURCE = "TEST_SOURCE";
  private static final String TEST_CHANNEL = "TEST_CHANNEL";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Mock private SmsRequestService smsRequestService;
  @Mock private SmsTemplateRepository smsTemplateRepository;
  @Mock private CaseRepository caseRepository;
  @Mock private NotificationClientApi notificationClientApi;

  @InjectMocks private SmsFulfilmentEndpoint smsFulfilmentEndpoint;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(smsFulfilmentEndpoint).build();
  }

  @Test
  void testSmsFulfilmentHappyPathWithUacQid() throws Exception {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    String expectedHashedUac = HashHelper.hash(newUacQid.getUac());
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(smsRequestService.isSmsTemplateAllowedOnSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);
    when(smsRequestService.fetchNewUacQidPairIfRequired(smsTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQid));

    RequestDTO smsFulfilmentRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("uacHash", is(expectedHashedUac)))
        .andExpect(jsonPath("uac").doesNotExist())
        .andExpect(jsonPath("qid", is(newUacQid.getQid())))
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verify(smsRequestService)
        .buildAndSendEnrichedSmsFulfilment(
            testCase.getId(),
            smsTemplate.getPackCode(),
            smsFulfilmentRequest.getPayload().getSmsFulfilment().getUacMetadata(),
            Optional.of(newUacQid),
            false,
            smsFulfilmentRequest.getHeader().getSource(),
            smsFulfilmentRequest.getHeader().getChannel(),
            smsFulfilmentRequest.getHeader().getCorrelationId(),
            smsFulfilmentRequest.getHeader().getOriginatingUser());

    // Check the SMS request
    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyTemplateId().toString()),
            eq(smsFulfilmentRequest.getPayload().getSmsFulfilment().getPhoneNumber()),
            templateValuesCaptor.capture(),
            eq(senderId));

    Map<String, String> actualSmsTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualSmsTemplateValues)
        .containsEntry(TEMPLATE_UAC_KEY, newUacQid.getUac())
        .containsEntry(TEMPLATE_QID_KEY, newUacQid.getQid());
  }

  @Test
  void testSmsFulfilmentHappyPathWithOnlyQid() throws Exception {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    String expectedHashedUac = HashHelper.hash(newUacQid.getUac());
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(smsRequestService.isSmsTemplateAllowedOnSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);
    when(smsRequestService.fetchNewUacQidPairIfRequired(smsTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQid));

    RequestDTO smsFulfilmentRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("uacHash", is(expectedHashedUac)))
        .andExpect(jsonPath("uac").doesNotExist())
        .andExpect(jsonPath("qid", is(newUacQid.getQid())))
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verify(smsRequestService)
        .buildAndSendEnrichedSmsFulfilment(
            testCase.getId(),
            smsTemplate.getPackCode(),
            smsFulfilmentRequest.getPayload().getSmsFulfilment().getUacMetadata(),
            Optional.of(newUacQid),
            false,
            smsFulfilmentRequest.getHeader().getSource(),
            smsFulfilmentRequest.getHeader().getChannel(),
            smsFulfilmentRequest.getHeader().getCorrelationId(),
            smsFulfilmentRequest.getHeader().getOriginatingUser());

    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyTemplateId().toString()),
            eq(smsFulfilmentRequest.getPayload().getSmsFulfilment().getPhoneNumber()),
            templateValuesCaptor.capture(),
            eq(senderId));

    Map<String, String> actualSmsTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualSmsTemplateValues)
        .containsEntry(TEMPLATE_QID_KEY, newUacQid.getQid())
        .containsOnlyKeys(TEMPLATE_QID_KEY);
  }

  @Test
  void testSmsFulfilmentHappyPathNoUacQid() throws Exception {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(smsRequestService.isSmsTemplateAllowedOnSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);
    when(smsRequestService.fetchNewUacQidPairIfRequired(smsTemplate.getTemplate()))
        .thenReturn(Optional.empty());

    RequestDTO smsFulfilmentRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("{}"))
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verify(smsRequestService)
        .buildAndSendEnrichedSmsFulfilment(
            testCase.getId(),
            smsTemplate.getPackCode(),
            smsFulfilmentRequest.getPayload().getSmsFulfilment().getUacMetadata(),
            Optional.empty(),
            false,
            smsFulfilmentRequest.getHeader().getSource(),
            smsFulfilmentRequest.getHeader().getChannel(),
            smsFulfilmentRequest.getHeader().getCorrelationId(),
            smsFulfilmentRequest.getHeader().getOriginatingUser());

    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyTemplateId().toString()),
            eq(smsFulfilmentRequest.getPayload().getSmsFulfilment().getPhoneNumber()),
            templateValuesCaptor.capture(),
            eq(senderId));

    Map<String, String> actualSmsTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualSmsTemplateValues).isEmpty();
  }

  @Test
  void testSmsFulfilmentServerErrorFromNotify() throws Exception {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(smsRequestService.isSmsTemplateAllowedOnSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(smsRequestService.fetchNewUacQidPairIfRequired(smsTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQid));
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);

    // Simulate an error when we attempt to send the SMS
    when(notificationClientApi.sendSms(any(), any(), any(), any()))
        .thenThrow(new NotificationClientException("Test"));

    RequestDTO smsFulfilmentRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When we call with the SMS fulfilment and the notify client errors, we get an internal server
    // error

    String expectedErrorMsg =
        "500 INTERNAL_SERVER_ERROR \"Error with Gov Notify when attempting to send SMS\";"
            + " nested exception is uk.gov.service.notify.NotificationClientException: Test";

    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(
            result ->
                assertThat(result.getResolvedException().getMessage()).isEqualTo(expectedErrorMsg))
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verify(smsRequestService)
        .buildAndSendEnrichedSmsFulfilment(
            testCase.getId(),
            smsTemplate.getPackCode(),
            smsFulfilmentRequest.getPayload().getSmsFulfilment().getUacMetadata(),
            Optional.of(newUacQid),
            false,
            smsFulfilmentRequest.getHeader().getSource(),
            smsFulfilmentRequest.getHeader().getChannel(),
            smsFulfilmentRequest.getHeader().getCorrelationId(),
            smsFulfilmentRequest.getHeader().getOriginatingUser());

    // Check the SMS request did still happen as expected
    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyTemplateId().toString()),
            eq(smsFulfilmentRequest.getPayload().getSmsFulfilment().getPhoneNumber()),
            templateValuesCaptor.capture(),
            eq(senderId));

    Map<String, String> actualSmsTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualSmsTemplateValues)
        .containsEntry(TEMPLATE_UAC_KEY, newUacQid.getUac())
        .containsEntry(TEMPLATE_QID_KEY, newUacQid.getQid());
  }

  @Test
  void testSmsFulfilmentInvalidPhoneNumber() throws Exception {
    // Given
    String invalidPhoneNumber = "07123 INVALID";
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(smsRequestService.isSmsTemplateAllowedOnSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(smsRequestService.validatePhoneNumber(invalidPhoneNumber))
        .thenReturn(false); // TODO how did this pass without this mock?

    RequestDTO smsFulfilmentRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), invalidPhoneNumber);

    // When we call with a bad phone number, we get a bad request response and descriptive reason
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("error", is("Invalid phone number")))
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verifyNoInteractions(notificationClientApi);
  }

  @Test
  void testValidateSmsFulfilmentRequestHappyPath() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO validRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(smsRequestService.isSmsTemplateAllowedOnSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(smsRequestService.validatePhoneNumber(VALID_PHONE_NUMBER)).thenReturn(true);

    // When validated, then no exception is thrown
    smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(validRequest, testCase, smsTemplate);
  }

  @Test
  void testValidateSmsFulfilmentRequestTemplateNotAllowedOnSurvey() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(smsRequestService.isSmsTemplateAllowedOnSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(false);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(
                    invalidRequest, testCase, smsTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage())
        .contains(
            "400 BAD_REQUEST \"The template for this pack code is not allowed on this survey\"");
  }

  @Test
  void testValidateSmsFulfilmentRequestNoSource() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);
    invalidRequest.getHeader().setSource(null);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(
                    invalidRequest, testCase, smsTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage())
        .isEqualTo(
            "400 BAD_REQUEST \"Invalid request header: correlationId, channel and source are mandatory\"");
  }

  @Test
  void testValidateSmsFulfilmentRequestNoChannel() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);
    invalidRequest.getHeader().setChannel(null);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(
                    invalidRequest, testCase, smsTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage())
        .isEqualTo(
            "400 BAD_REQUEST \"Invalid request header: correlationId, channel and source are mandatory\"");
  }

  @Test
  void testValidateSmsFulfilmentRequestNoCorrelationId() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);
    invalidRequest.getHeader().setCorrelationId(null);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(
                    invalidRequest, testCase, smsTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage())
        .isEqualTo(
            "400 BAD_REQUEST \"Invalid request header: correlationId, channel and source are mandatory\"");
    assertThat(thrown.getMessage())
        .isEqualTo(
            "400 BAD_REQUEST \"Invalid request header: correlationId, channel and source are mandatory\"");
  }

  private RequestDTO buildSmsFulfilmentRequest(UUID caseId, String packCode, String phoneNumber) {
    RequestDTO smsFulfilmentEvent = new RequestDTO();
    RequestHeaderDTO header = new RequestHeaderDTO();
    header.setSource(TEST_SOURCE);
    header.setChannel(TEST_CHANNEL);
    header.setCorrelationId(UUID.randomUUID());

    RequestPayloadDTO payload = new RequestPayloadDTO();
    SmsFulfilment smsFulfilment = new SmsFulfilment();
    smsFulfilment.setCaseId(caseId);
    smsFulfilment.setPackCode(packCode);
    smsFulfilment.setPhoneNumber(phoneNumber);

    smsFulfilmentEvent.setHeader(header);
    payload.setSmsFulfilment(smsFulfilment);
    smsFulfilmentEvent.setPayload(payload);
    return smsFulfilmentEvent;
  }

  private UacQidCreatedPayloadDTO getUacQidCreated() {
    UacQidCreatedPayloadDTO uacQidCreatedPayloadDTO = new UacQidCreatedPayloadDTO();
    uacQidCreatedPayloadDTO.setUac("test_uac");
    uacQidCreatedPayloadDTO.setQid("01_test_qid");
    return uacQidCreatedPayloadDTO;
  }

  private SmsTemplate getTestSmsTemplate(String[] template) {
    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setNotifyTemplateId(UUID.randomUUID());
    smsTemplate.setPackCode("TEST");
    smsTemplate.setTemplate(template);
    return smsTemplate;
  }

  private Case getTestCase() {
    Case caze = new Case();
    CollectionExercise collex = new CollectionExercise();
    Survey survey = new Survey();
    collex.setSurvey(survey);
    caze.setId(UUID.randomUUID());
    caze.setCollectionExercise(collex);
    return caze;
  }
}
