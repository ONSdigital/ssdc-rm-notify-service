package uk.gov.ons.ssdc.notifysvc.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import uk.gov.ons.ssdc.notifysvc.model.dto.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.ResponseManagementEvent;
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

    ResponseManagementEvent smsFulfilmentEvent =
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
    ResponseManagementEvent actualEnrichedSmsEvent =
        objectMapper.readValue(pubsubMessage, ResponseManagementEvent.class);
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getUac())
        .isEqualTo(newUacQid.getUac());
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getQid())
        .isEqualTo(newUacQid.getQid());

    // Check the
    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getTemplateId().toString()),
            eq(smsFulfilmentEvent.getPayload().getSmsFulfilment().getPhoneNumber()),
            templateValuesCaptor.capture(),
            eq(senderId));

    Map<String, String> actualSmsTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualSmsTemplateValues).containsEntry(SMS_TEMPLATE_UAC_KEY, newUacQid.getUac());
    assertThat(actualSmsTemplateValues).containsEntry(SMS_TEMPLATE_QID_KEY, newUacQid.getQid());
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

    ResponseManagementEvent smsFulfilmentEvent =
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
    ResponseManagementEvent actualEnrichedSmsEvent =
        objectMapper.readValue(pubsubMessage, ResponseManagementEvent.class);
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment()).isNotNull();
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getUac())
        .isEqualTo(newUacQid.getUac());
    assertThat(actualEnrichedSmsEvent.getPayload().getEnrichedSmsFulfilment().getQid())
        .isEqualTo(newUacQid.getQid());

    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendSms(
            eq(smsTemplate.getTemplateId().toString()),
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

    ResponseManagementEvent smsFulfilmentEvent =
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
            eq(smsTemplate.getTemplateId().toString()),
            eq(smsFulfilmentEvent.getPayload().getSmsFulfilment().getPhoneNumber()),
            templateValuesCaptor.capture(),
            eq(senderId));

    Map<String, String> actualSmsTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualSmsTemplateValues).containsOnlyKeys();
  }

  @Test
  void testValidateSmsFulfilmentEventHappyPath() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    ResponseManagementEvent validEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(fulfilmentSurveySmsTemplateRepository.existsBySmsTemplateAndSurvey(
            smsTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);

    // When validated, then no exception is thrown
    smsFulfilmentEndpoint.validateSmsFulfilmentEvent(validEvent);
  }

  @Test
  void testValidateSmsFulfilmentEventCaseNotFound() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    ResponseManagementEvent invalidEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.empty());

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validateSmsFulfilmentEvent(invalidEvent));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testValidateSmsFulfilmentEventPackCodeNotFound() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    ResponseManagementEvent invalidEvent =
        buildSmsFulfilmentEvent(testCase.getId(), smsTemplate.getPackCode(), VALID_PHONE_NUMBER);

    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsTemplateRepository.findById(smsTemplate.getPackCode())).thenReturn(Optional.empty());

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () -> smsFulfilmentEndpoint.validateSmsFulfilmentEvent(invalidEvent));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testValidateSmsFulfilmentEventTemplateNotAllowedOnSurvey() {
    // Given
    Case testCase = getTestCase();
    SmsTemplate smsTemplate = getTestSmsTemplate(new String[] {});
    ResponseManagementEvent invalidEvent =
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
            () -> smsFulfilmentEndpoint.validateSmsFulfilmentEvent(invalidEvent));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private ResponseManagementEvent buildSmsFulfilmentEvent(
      UUID caseId, String packCode, String phoneNumber) {
    ResponseManagementEvent smsFulfilmentEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    PayloadDTO payloadDTO = new PayloadDTO();
    SmsFulfilment smsFulfilment = new SmsFulfilment();
    smsFulfilment.setCaseId(caseId);
    smsFulfilment.setPackCode(packCode);
    smsFulfilment.setPhoneNumber(phoneNumber);

    smsFulfilmentEvent.setEvent(event);
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
    smsTemplate.setTemplateId(UUID.randomUUID());
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
