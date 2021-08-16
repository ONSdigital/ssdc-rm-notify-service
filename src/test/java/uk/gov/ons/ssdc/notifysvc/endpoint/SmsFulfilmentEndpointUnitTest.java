package uk.gov.ons.ssdc.notifysvc.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
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
import uk.gov.ons.ssdc.notifysvc.client.UacQidServiceClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.entity.Case;
import uk.gov.ons.ssdc.notifysvc.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.notifysvc.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.utility.PubSubHelper;
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
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(uacQidServiceClient.generateUacQid(QID_TYPE)).thenReturn(newUacQid);

    EventDTO smsFulfilmentEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentEvent))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verify(uacQidServiceClient).generateUacQid(QID_TYPE);

    // Check the pubsub message
    ArgumentCaptor<byte[]> pubSubMessageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(pubSubHelper).publishAndConfirm(eq(smsFulfilmentTopic), pubSubMessageCaptor.capture());
    byte[] pubsubMessage = pubSubMessageCaptor.getValue();
    EventDTO actualEnrichedSmsEvent = objectMapper.readValue(pubsubMessage, EventDTO.class);
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getUac())
        .isEqualTo(newUacQid.getUac());
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getQid())
        .isEqualTo(newUacQid.getQid());

    // Check the SMS request
    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyId().toString()),
            eq(smsFulfilmentEvent.getPayload().getSmsFulfilment().getPhoneNumber()),
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
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(uacQidServiceClient.generateUacQid(QID_TYPE)).thenReturn(newUacQid);

    EventDTO smsFulfilmentEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentEvent))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verify(uacQidServiceClient).generateUacQid(QID_TYPE);

    ArgumentCaptor<byte[]> pubSubMessageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(pubSubHelper).publishAndConfirm(eq(smsFulfilmentTopic), pubSubMessageCaptor.capture());
    byte[] pubsubMessage = pubSubMessageCaptor.getValue();
    EventDTO actualEnrichedSmsEvent = objectMapper.readValue(pubsubMessage, EventDTO.class);
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getUac())
        .isEqualTo(newUacQid.getUac());
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getQid())
        .isEqualTo(newUacQid.getQid());

    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyId().toString()),
            eq(smsFulfilmentEvent.getPayload().getSmsFulfilment().getPhoneNumber()),
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
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);

    EventDTO smsFulfilmentEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentEvent))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verifyNoInteractions(uacQidServiceClient);
    verify(pubSubHelper).publishAndConfirm(eq(smsFulfilmentTopic), any());
    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyId().toString()),
            eq(smsFulfilmentEvent.getPayload().getSmsFulfilment().getPhoneNumber()),
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

    EventDTO smsFulfilmentEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    // When we call with the SMS fulfilment and the notify client errors, we get an internal server
    // error
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentEvent))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    // The event should still have been sent to be safe
    ArgumentCaptor<byte[]> pubSubMessageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(pubSubHelper).publishAndConfirm(eq(smsFulfilmentTopic), pubSubMessageCaptor.capture());
    byte[] pubsubMessage = pubSubMessageCaptor.getValue();
    EventDTO actualEnrichedSmsEvent = objectMapper.readValue(pubsubMessage, EventDTO.class);
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getUac())
        .isEqualTo(newUacQid.getUac());
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getQid())
        .isEqualTo(newUacQid.getQid());

    // Check the SMS request did still happen as expected
    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getNotifyId().toString()),
            eq(smsFulfilmentEvent.getPayload().getSmsFulfilment().getPhoneNumber()),
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

    EventDTO smsFulfilmentEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), "07123 INVALID");

    // When we call with a bad phone number, we get a bad request response and descriptive reason
    mockMvc
        .perform(
            post(SMS_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(smsFulfilmentEvent))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(status().reason(containsString("Invalid phone number")))
        .andExpect(handler().handlerType(SmsFulfilmentEndpoint.class));

    // Then
    verifyNoInteractions(uacQidServiceClient);
    verifyNoInteractions(pubSubHelper);
    verifyNoInteractions(notificationClientApi);
  }

  @Test
  void testValidateSmsFulfilmentEventHappyPath() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    EventDTO validEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);

    // When validated, then no exception is thrown
    smsFulfilmentEndpoint.validateEventAndFetchTemplate(validEvent);
  }

  @Test
  void testValidateSmsFulfilmentEventCaseNotFound() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    EventDTO invalidEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.empty());

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validateEventAndFetchTemplate(invalidEvent));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testValidateSmsFulfilmentEventPackCodeNotFound() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    EventDTO invalidEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode())).thenReturn(Optional.empty());

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validateEventAndFetchTemplate(invalidEvent));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testValidateSmsFulfilmentEventTemplateNotAllowedOnSurvey() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    EventDTO invalidEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

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
            () -> smsFulfilmentEndpoint.validateEventAndFetchTemplate(invalidEvent));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        VALID_PHONE_NUMBER,
        "+447123456789",
        "0447123456789",
        "(+44)7123456789",
        "{+44}7123456789",
        "[+44]7123456789",
        "+44 7123456789",
        "07123 456789",
        "(07123) 456789",
        "07123,456789",
        "07123--456789",
        "0-7-1-2-3-4-5-6-7-8-9",
        "0 7 1 2 3 4 5 6 7 8 9",
        "07123\t456789",
        "07123\n456789",
        "0.7-123456789",
        "0  7123    456789",
      })
  void testValidatePhoneNumberValid(String phoneNumber) {
    try {
      smsFulfilmentEndpoint.validatePhoneNumber(phoneNumber);
    } catch (ResponseStatusException e) {
      fail("Validation failed on valid phone number: " + phoneNumber, e);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1",
        "foo",
        "007",
        "071234567890",
        "44+7123456789",
        "0712345678a",
        "@7123456789",
        "(+44) 07123456789"
      })
  void testValidatePhoneNumberInvalid(String phoneNumber) {
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validatePhoneNumber(phoneNumber));
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private EventDTO buildSmsFulfilmentEvent(UUID caseId, String packCode, String phoneNumber) {
    EventDTO smsFulfilmentEvent = new EventDTO();
    EventHeaderDTO event = new EventHeaderDTO();
    PayloadDTO payloadDTO = new PayloadDTO();
    SmsFulfilment smsFulfilment = new SmsFulfilment();
    smsFulfilment.setCaseId(caseId);
    smsFulfilment.setPackCode(packCode);
    smsFulfilment.setPhoneNumber(phoneNumber);

    smsFulfilmentEvent.setEventHeader(event);
    payloadDTO.setSmsFulfilment(smsFulfilment);
    smsFulfilmentEvent.setPayload(payloadDTO);
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
    smsTemplate.setNotifyId(UUID.randomUUID());
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
