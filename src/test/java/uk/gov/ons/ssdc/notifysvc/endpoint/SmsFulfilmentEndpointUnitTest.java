package uk.gov.ons.ssdc.notifysvc.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.RequestDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.RequestHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.RequestPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;
import uk.gov.ons.ssdc.notifysvc.utils.HashHelper;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@ExtendWith(MockitoExtension.class)
class SmsFulfilmentEndpointUnitTest {

  @Value("${queueconfig.sms-fulfilment-topic}")
  private String smsFulfilmentTopic;

  @Value("${notify.senderId}")
  private String senderId;

  private static final String SMS_TEMPLATE_UAC_KEY = "__uac__";
  private static final String SMS_TEMPLATE_QID_KEY = "__qid__";

  private static final String SMS_FULFILMENT_ENDPOINT = "/sms-fulfilment";

  private static final String VALID_PHONE_NUMBER = "07123456789";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final int QID_TYPE = 1;

  static {
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Mock private SmsTemplateRepository smsTemplateRepository;
  @Mock private CaseRepository caseRepository;
  @Mock private FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  @Mock private UacQidServiceClient uacQidServiceClient;
  @Mock private PubSubHelper pubSubHelper;
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
    SmsTemplate smsTemplate =
        getTestSmsTemplate(new String[] {SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    String expectedHashedUac = HashHelper.hash(newUacQid.getUac());
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(uacQidServiceClient.generateUacQid(QID_TYPE)).thenReturn(newUacQid);

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
    verify(uacQidServiceClient).generateUacQid(QID_TYPE);

    // Check the pubsub message
    ArgumentCaptor<EventDTO> pubSubMessageCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper).publishAndConfirm(eq(smsFulfilmentTopic), pubSubMessageCaptor.capture());
    EventDTO actualEnrichedSmsEvent = pubSubMessageCaptor.getValue();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getUac())
        .isEqualTo(newUacQid.getUac());
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getQid())
        .isEqualTo(newUacQid.getQid());
    assertThat(actualEnrichedSmsEvent.getHeader().getTopic()).isEqualTo(smsFulfilmentTopic);

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
        .containsEntry(SMS_TEMPLATE_UAC_KEY, newUacQid.getUac())
        .containsEntry(SMS_TEMPLATE_QID_KEY, newUacQid.getQid());
  }

  @Test
  void testSmsFulfilmentHappyPathWithOnlyQid() throws Exception {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {SMS_TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    String expectedHashedUac = HashHelper.hash(newUacQid.getUac());
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(uacQidServiceClient.generateUacQid(QID_TYPE)).thenReturn(newUacQid);

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
    verify(uacQidServiceClient).generateUacQid(QID_TYPE);

    ArgumentCaptor<EventDTO> pubSubMessageCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper).publishAndConfirm(eq(smsFulfilmentTopic), pubSubMessageCaptor.capture());
    EventDTO actualEnrichedSmsEvent = pubSubMessageCaptor.getValue();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getUac())
        .isEqualTo(newUacQid.getUac());
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getQid())
        .isEqualTo(newUacQid.getQid());
    assertThat(actualEnrichedSmsEvent.getHeader().getTopic()).isEqualTo(smsFulfilmentTopic);
    assertThat(actualEnrichedSmsEvent.getHeader().getMessageId()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getHeader().getDateTime()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getHeader().getVersion())
        .isEqualTo(Constants.EVENT_SCHEMA_VERSION);
    assertThat(actualEnrichedSmsEvent.getHeader().getSource())
        .isEqualTo(smsFulfilmentRequest.getHeader().getSource());
    assertThat(actualEnrichedSmsEvent.getHeader().getChannel())
        .isEqualTo(smsFulfilmentRequest.getHeader().getChannel());

    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyTemplateId().toString()),
            eq(smsFulfilmentRequest.getPayload().getSmsFulfilment().getPhoneNumber()),
            templateValuesCaptor.capture(),
            eq(senderId));

    Map<String, String> actualSmsTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualSmsTemplateValues)
        .containsEntry(SMS_TEMPLATE_QID_KEY, newUacQid.getQid())
        .containsOnlyKeys(SMS_TEMPLATE_QID_KEY);
  }

  @Test
  void testSmsFulfilmentHappyPathNoUacQid() throws Exception {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);

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
    verifyNoInteractions(uacQidServiceClient);
    verify(pubSubHelper).publishAndConfirm(eq(smsFulfilmentTopic), any());
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
    SmsTemplate smsTemplate =
        getTestSmsTemplate(new String[] {SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(uacQidServiceClient.generateUacQid(QID_TYPE)).thenReturn(newUacQid);

    // Simulate an error when we attempt to send the SMS
    when(notificationClientApi.sendSms(any(), any(), any(), any()))
        .thenThrow(new NotificationClientException("Test"));

    RequestDTO smsFulfilmentRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When we call with the SMS fulfilment and the notify client errors, we get an internal server
    // error
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    ArgumentCaptor<EventDTO> pubSubMessageCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper).publishAndConfirm(eq(smsFulfilmentTopic), pubSubMessageCaptor.capture());
    EventDTO actualEnrichedSmsEvent = pubSubMessageCaptor.getValue();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getUac())
        .isEqualTo(newUacQid.getUac());
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getQid())
        .isEqualTo(newUacQid.getQid());
    assertThat(actualEnrichedSmsEvent.getHeader().getTopic()).isEqualTo(smsFulfilmentTopic);

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
        .containsEntry(SMS_TEMPLATE_UAC_KEY, newUacQid.getUac())
        .containsEntry(SMS_TEMPLATE_QID_KEY, newUacQid.getQid());
  }

  @Test
  void testSmsFulfilmentInvalidPhoneNumber() throws Exception {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);

    RequestDTO smsFulfilmentRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), "07123 INVALID");

    // When we call with a bad phone number, we get a bad request response and descriptive reason
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("Invalid phone number")))
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verifyNoInteractions(uacQidServiceClient);
    verifyNoInteractions(pubSubHelper);
    verifyNoInteractions(notificationClientApi);
  }

  @Test
  void testValidateSmsFulfilmentRequestHappyPath() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO validRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);

    // When validated, then no exception is thrown
    smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(validRequest);
  }

  @Test
  void testValidateSmsFulfilmentRequestCaseNotFound() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.empty());

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(invalidRequest));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testValidateSmsFulfilmentRequestPackCodeNotFound() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode())).thenReturn(Optional.empty());

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(invalidRequest));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testValidateSmsFulfilmentRequestTemplateNotAllowedOnSurvey() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildSmsFulfilmentRequest(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(false);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(invalidRequest));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage()).contains("pack code is not allowed on this survey");
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
            () -> smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(invalidRequest));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage()).contains("Invalid request header");
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
            () -> smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(invalidRequest));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage()).contains("Invalid request header");
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
            () -> smsFulfilmentEndpoint.validateRequestAndFetchSmsTemplate(invalidRequest));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage()).contains("Invalid request header");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        VALID_PHONE_NUMBER,
        "+447123456789",
        "0447123456789",
        "7123456789",
      })
  void testValidatePhoneNumberValid(String phoneNumber) {
    try {
      smsFulfilmentEndpoint.validatePhoneNumber(phoneNumber);
    } catch (ResponseStatusException e) {
      fail("Validation failed on valid phone number: " + phoneNumber, e);
    }
  }

  private RequestDTO buildSmsFulfilmentRequest(UUID caseId, String packCode, String phoneNumber) {
    RequestDTO smsFulfilmentEvent = new RequestDTO();
    RequestHeaderDTO header = new RequestHeaderDTO();
    header.setSource("TEST_SOURCE");
    header.setChannel("TEST_CHANNEL");
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
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validatePhoneNumber(phoneNumber));
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
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
