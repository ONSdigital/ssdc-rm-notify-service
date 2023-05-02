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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.EmailFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.EmailRequestService;
import uk.gov.ons.ssdc.notifysvc.utils.HashHelper;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@ExtendWith(MockitoExtension.class)
class EmailFulfilmentEndpointTest {

  private static final String EMAIL_FULFILMENT_ENDPOINT = "/email-fulfilment";

  private static final String VALID_EMAIL_ADDRESS = "example@example.com";
  private static final String TEST_SOURCE = "TEST_SOURCE";
  private static final String TEST_CHANNEL = "TEST_CHANNEL";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Mock private EmailRequestService emailRequestService;
  @Mock private EmailTemplateRepository emailTemplateRepository;
  @Mock private CaseRepository caseRepository;
  @Mock private NotificationClientApi notificationClientApi;

  @InjectMocks private EmailFulfilmentEndpoint emailFulfilmentEndpoint;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(emailFulfilmentEndpoint).build();
  }

  @Test
  void testEmailFulfilmentHappyPathWithUacQid() throws Exception {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate =
        getTestEmailTemplate(new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    String expectedHashedUac = HashHelper.hash(newUacQid.getUac());
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(emailRequestService.isEmailTemplateAllowedOnSurvey(
            emailTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());
    when(emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQid));

    RequestDTO emailFulfilmentRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);

    // When
    mockMvc
        .perform(
            post(EMAIL_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(emailFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("uacHash", is(expectedHashedUac)))
        .andExpect(jsonPath("uac").doesNotExist())
        .andExpect(jsonPath("qid", is(newUacQid.getQid())))
        .andExpect(handler().handlerType(EmailFulfilmentEndpoint.class));

    // Then
    verify(emailRequestService)
        .buildAndSendEmailConfirmation(
            testCase.getId(),
            emailTemplate.getPackCode(),
            emailFulfilmentRequest.getPayload().getEmailFulfilment().getUacMetadata(),
            emailFulfilmentRequest.getPayload().getEmailFulfilment().getPersonalisation(),
            Optional.of(newUacQid),
            false,
            emailFulfilmentRequest.getHeader().getSource(),
            emailFulfilmentRequest.getHeader().getChannel(),
            emailFulfilmentRequest.getHeader().getCorrelationId(),
            emailFulfilmentRequest.getHeader().getOriginatingUser());

    // Check the email request
    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendEmail(
            eq(emailTemplate.getNotifyTemplateId().toString()),
            eq(emailFulfilmentRequest.getPayload().getEmailFulfilment().getEmail()),
            templateValuesCaptor.capture(),
            eq(emailFulfilmentRequest.getHeader().getCorrelationId().toString()));

    Map<String, String> actualEmailTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualEmailTemplateValues)
        .containsEntry(TEMPLATE_UAC_KEY, newUacQid.getUac())
        .containsEntry(TEMPLATE_QID_KEY, newUacQid.getQid());
  }

  @Test
  void testEmailFulfilmentHappyPathWithOnlyQid() throws Exception {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    String expectedHashedUac = HashHelper.hash(newUacQid.getUac());
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(emailRequestService.isEmailTemplateAllowedOnSurvey(
            emailTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());
    when(emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQid));

    RequestDTO emailFulfilmentRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);

    // When
    mockMvc
        .perform(
            post(EMAIL_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(emailFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("uacHash", is(expectedHashedUac)))
        .andExpect(jsonPath("uac").doesNotExist())
        .andExpect(jsonPath("qid", is(newUacQid.getQid())))
        .andExpect(handler().handlerType(EmailFulfilmentEndpoint.class));

    // Then
    verify(emailRequestService)
        .buildAndSendEmailConfirmation(
            testCase.getId(),
            emailTemplate.getPackCode(),
            emailFulfilmentRequest.getPayload().getEmailFulfilment().getUacMetadata(),
            emailFulfilmentRequest.getPayload().getEmailFulfilment().getPersonalisation(),
            Optional.of(newUacQid),
            false,
            emailFulfilmentRequest.getHeader().getSource(),
            emailFulfilmentRequest.getHeader().getChannel(),
            emailFulfilmentRequest.getHeader().getCorrelationId(),
            emailFulfilmentRequest.getHeader().getOriginatingUser());

    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendEmail(
            eq(emailTemplate.getNotifyTemplateId().toString()),
            eq(emailFulfilmentRequest.getPayload().getEmailFulfilment().getEmail()),
            templateValuesCaptor.capture(),
            eq(emailFulfilmentRequest.getHeader().getCorrelationId().toString()));

    Map<String, String> actualEmailTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualEmailTemplateValues)
        .containsEntry(TEMPLATE_QID_KEY, newUacQid.getQid())
        .containsOnlyKeys(TEMPLATE_QID_KEY);
  }

  @Test
  void testEmailFulfilmentHappyPathNoUacQid() throws Exception {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(emailRequestService.isEmailTemplateAllowedOnSurvey(
            emailTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());
    when(emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate()))
        .thenReturn(Optional.empty());

    RequestDTO emailFulfilmentRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);

    // When
    mockMvc
        .perform(
            post(EMAIL_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(emailFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("{}"))
        .andExpect(handler().handlerType(EmailFulfilmentEndpoint.class));

    // Then
    verify(emailRequestService)
        .buildAndSendEmailConfirmation(
            testCase.getId(),
            emailTemplate.getPackCode(),
            emailFulfilmentRequest.getPayload().getEmailFulfilment().getUacMetadata(),
            emailFulfilmentRequest.getPayload().getEmailFulfilment().getPersonalisation(),
            Optional.empty(),
            false,
            emailFulfilmentRequest.getHeader().getSource(),
            emailFulfilmentRequest.getHeader().getChannel(),
            emailFulfilmentRequest.getHeader().getCorrelationId(),
            emailFulfilmentRequest.getHeader().getOriginatingUser());

    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendEmail(
            eq(emailTemplate.getNotifyTemplateId().toString()),
            eq(emailFulfilmentRequest.getPayload().getEmailFulfilment().getEmail()),
            templateValuesCaptor.capture(),
            eq(emailFulfilmentRequest.getHeader().getCorrelationId().toString()));

    Map<String, String> actualEmailTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualEmailTemplateValues).isEmpty();
  }

  @Test
  void testEmailFulfilmentServerErrorFromNotify() throws Exception {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate =
        getTestEmailTemplate(new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});
    UacQidCreatedPayloadDTO newUacQid = getUacQidCreated();
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(emailRequestService.isEmailTemplateAllowedOnSurvey(
            emailTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate()))
        .thenReturn(Optional.of(newUacQid));
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());

    // Simulate an error when we attempt to send the email
    when(notificationClientApi.sendEmail(any(), any(), any(), any()))
        .thenThrow(new NotificationClientException("Test"));

    RequestDTO emailFulfilmentRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);

    // When we call with the email fulfilment and the notify client errors, we get an internal
    // server
    // error
    mockMvc
        .perform(
            post(EMAIL_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(emailFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(handler().handlerType(EmailFulfilmentEndpoint.class));

    // Then
    verify(emailRequestService)
        .buildAndSendEmailConfirmation(
            testCase.getId(),
            emailTemplate.getPackCode(),
            emailFulfilmentRequest.getPayload().getEmailFulfilment().getUacMetadata(),
            emailFulfilmentRequest.getPayload().getEmailFulfilment().getPersonalisation(),
            Optional.of(newUacQid),
            false,
            emailFulfilmentRequest.getHeader().getSource(),
            emailFulfilmentRequest.getHeader().getChannel(),
            emailFulfilmentRequest.getHeader().getCorrelationId(),
            emailFulfilmentRequest.getHeader().getOriginatingUser());

    // Check the email request did still happen as expected
    ArgumentCaptor<Map<String, String>> templateValuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationClientApi)
        .sendEmail(
            eq(emailTemplate.getNotifyTemplateId().toString()),
            eq(emailFulfilmentRequest.getPayload().getEmailFulfilment().getEmail()),
            templateValuesCaptor.capture(),
            eq(emailFulfilmentRequest.getHeader().getCorrelationId().toString()));

    Map<String, String> actualEmailTemplateValues = templateValuesCaptor.getValue();
    assertThat(actualEmailTemplateValues)
        .containsEntry(TEMPLATE_UAC_KEY, newUacQid.getUac())
        .containsEntry(TEMPLATE_QID_KEY, newUacQid.getQid());
  }

  @Test
  void testEmailFulfilmentInvalidEmailAddress() throws Exception {
    // Given
    String invalidEmailAddress = "not.valid";
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(emailRequestService.isEmailTemplateAllowedOnSurvey(
            emailTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(emailRequestService.validateEmailAddress(invalidEmailAddress))
        .thenReturn(
            Optional.of(
                "Mock email address is invalid")); // TODO how did this pass without this mock?

    RequestDTO emailFulfilmentRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), invalidEmailAddress);

    // When we call with a bad email address, we get a bad request response and descriptive reason
    mockMvc
        .perform(
            post(EMAIL_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(emailFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("error", is("Invalid email address: Mock email address is invalid")))
        .andExpect(handler().handlerType(EmailFulfilmentEndpoint.class));

    // Then
    verifyNoInteractions(notificationClientApi);
  }

  @Test
  void testEmailFulfilmentTemplateNotAllowedOnSurvey() throws Exception {
    // Given
    String invalidEmailAddress = "not.valid";
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(emailTemplateRepository.findById(emailTemplate.getPackCode()))
        .thenReturn(Optional.of(emailTemplate));
    when(emailRequestService.isEmailTemplateAllowedOnSurvey(
            emailTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(false);

    RequestDTO emailFulfilmentRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), invalidEmailAddress);

    // When we call with a bad email address, we get a bad request response and descriptive reason
    mockMvc
        .perform(
            post(EMAIL_FULFILMENT_ENDPOINT)
                .content(objectMapper.writeValueAsBytes(emailFulfilmentRequest))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("error", is("The template for this pack code is not allowed on this survey")))
        .andExpect(handler().handlerType(EmailFulfilmentEndpoint.class));

    // Then
    verifyNoInteractions(notificationClientApi);
  }

  @Test
  void testValidateEmailFulfilmentRequestHappyPath() {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    RequestDTO validRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);

    when(emailRequestService.isEmailTemplateAllowedOnSurvey(
            emailTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(true);
    when(emailRequestService.validateEmailAddress(VALID_EMAIL_ADDRESS))
        .thenReturn(Optional.empty());

    // When validated, then no exception is thrown
    emailFulfilmentEndpoint.validateRequestAndFetchEmailTemplate(
        validRequest, testCase, emailTemplate);
  }

  @Test
  void testValidateEmailFulfilmentRequestCaseNotFound() {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                emailFulfilmentEndpoint.validateRequestAndFetchEmailTemplate(
                    invalidRequest, testCase, emailTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testValidateEmailFulfilmentRequestPackCodeNotFound() {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                emailFulfilmentEndpoint.validateRequestAndFetchEmailTemplate(
                    invalidRequest, testCase, emailTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testValidateEmailFulfilmentRequestTemplateNotAllowedOnSurvey() {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);

    when(emailRequestService.isEmailTemplateAllowedOnSurvey(
            emailTemplate, testCase.getCollectionExercise().getSurvey()))
        .thenReturn(false);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                emailFulfilmentEndpoint.validateRequestAndFetchEmailTemplate(
                    invalidRequest, testCase, emailTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage()).contains("pack code is not allowed on this survey");
  }

  @Test
  void testValidateEmailFulfilmentRequestNoSource() {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);
    invalidRequest.getHeader().setSource(null);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                emailFulfilmentEndpoint.validateRequestAndFetchEmailTemplate(
                    invalidRequest, testCase, emailTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage())
        .contains(
            "400 BAD_REQUEST \"Invalid request header: correlationId, channel and source are mandatory\"");
  }

  @Test
  void testValidateEmailFulfilmentRequestNoChannel() {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);
    invalidRequest.getHeader().setChannel(null);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                emailFulfilmentEndpoint.validateRequestAndFetchEmailTemplate(
                    invalidRequest, testCase, emailTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage())
        .contains(
            "400 BAD_REQUEST \"Invalid request header: correlationId, channel and source are mandatory\"");
  }

  @Test
  void testValidateEmailFulfilmentRequestNoCorrelationId() {
    // Given
    Case testCase = getTestCase();
    EmailTemplate emailTemplate = getTestEmailTemplate(new String[] {});
    RequestDTO invalidRequest =
        buildEmailFulfilmentRequest(
            testCase.getId(), emailTemplate.getPackCode(), VALID_EMAIL_ADDRESS);
    invalidRequest.getHeader().setCorrelationId(null);

    // When
    ResponseStatusException thrown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                emailFulfilmentEndpoint.validateRequestAndFetchEmailTemplate(
                    invalidRequest, testCase, emailTemplate));

    // Then
    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessage())
        .contains(
            "400 BAD_REQUEST \"Invalid request header: correlationId, channel and source are mandatory\"");
  }

  private RequestDTO buildEmailFulfilmentRequest(
      UUID caseId, String packCode, String emailAddress) {
    RequestDTO emailFulfilmentEvent = new RequestDTO();
    RequestHeaderDTO header = new RequestHeaderDTO();
    header.setSource(TEST_SOURCE);
    header.setChannel(TEST_CHANNEL);
    header.setCorrelationId(UUID.randomUUID());

    RequestPayloadDTO payload = new RequestPayloadDTO();
    EmailFulfilment emailFulfilment = new EmailFulfilment();
    emailFulfilment.setCaseId(caseId);
    emailFulfilment.setPackCode(packCode);
    emailFulfilment.setEmail(emailAddress);

    emailFulfilmentEvent.setHeader(header);
    payload.setEmailFulfilment(emailFulfilment);
    emailFulfilmentEvent.setPayload(payload);
    return emailFulfilmentEvent;
  }

  private UacQidCreatedPayloadDTO getUacQidCreated() {
    UacQidCreatedPayloadDTO uacQidCreatedPayloadDTO = new UacQidCreatedPayloadDTO();
    uacQidCreatedPayloadDTO.setUac("test_uac");
    uacQidCreatedPayloadDTO.setQid("01_test_qid");
    return uacQidCreatedPayloadDTO;
  }

  private EmailTemplate getTestEmailTemplate(String[] template) {
    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setNotifyTemplateId(UUID.randomUUID());
    emailTemplate.setPackCode("TEST");
    emailTemplate.setTemplate(template);
    return emailTemplate;
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
