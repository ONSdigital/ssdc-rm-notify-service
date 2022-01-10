package uk.gov.ons.ssdc.notifysvc.messaging;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.common.model.entity.CollectionInstrumentSelectionRule;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveyEmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;
import uk.gov.ons.ssdc.notifysvc.model.dto.NotifyApiSendEmailResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailConfirmation;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequest;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.CollectionExerciseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveyEmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SurveyRepository;
import uk.gov.ons.ssdc.notifysvc.testUtils.PubSubTestHelper;
import uk.gov.ons.ssdc.notifysvc.testUtils.QueueSpy;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmailRequestReceiverIT {

  @Autowired private CaseRepository caseRepository;
  @Autowired private SurveyRepository surveyRepository;
  @Autowired private CollectionExerciseRepository collectionExerciseRepository;
  @Autowired private EmailTemplateRepository emailTemplateRepository;

  @Autowired
  private FulfilmentSurveyEmailTemplateRepository fulfilmentSurveyEmailTemplateRepository;

  @Autowired private PubSubTestHelper pubSubTestHelper;
  @Autowired private PubSubHelper pubSubHelper;

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final EasyRandom easyRandom = new EasyRandom();

  private static final String EMAIL_REQUEST_TOPIC = "rm-internal-email-request";
  private static final String TEST_EMAIL_REQUEST_ENRICHED_SUBSCRIPTION =
      "TEST-email-request-enriched_notify-service";

  private static final String ENRICHED_EMAIL_FULFILMENT_SUBSCRIPTION =
      "rm-internal-email-fulfilment_notify-service-it";

  private static final Map<String, String> TEST_COLLECTION_EXERCISE_UPDATE_METADATA =
      Map.of("TEST_COLLECTION_EXERCISE_UPDATE_METADATA", "TEST");

  @Value("${queueconfig.email-request-enriched-topic}")
  private String emailRequestEnrichedTopic;

  @Value("${queueconfig.email-confirmation-topic}")
  private String emailConfirmationTopic;

  public static final String EMAIL_NOTIFY_API_ENDPOINT = "/v2/notifications/email";

  private WireMockServer wireMockServer;

  @BeforeEach
  @Transactional
  public void setUp() {
    clearDownData();
    pubSubTestHelper.purgeMessages(
        TEST_EMAIL_REQUEST_ENRICHED_SUBSCRIPTION, emailRequestEnrichedTopic);
    pubSubTestHelper.purgeMessages(ENRICHED_EMAIL_FULFILMENT_SUBSCRIPTION, emailConfirmationTopic);
    this.wireMockServer = new WireMockServer(8089);
    wireMockServer.start();
    configureFor(wireMockServer.port());
  }

  public void clearDownData() {
    fulfilmentSurveyEmailTemplateRepository.deleteAllInBatch();
    emailTemplateRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
    collectionExerciseRepository.deleteAllInBatch();
    surveyRepository.deleteAllInBatch();
  }

  @AfterEach
  public void tearDown() {
    wireMockServer.stop();
    clearDownData();
  }

  @Test
  void testReceiveEmailRequest() throws InterruptedException, JsonProcessingException {
    // Given
    // Set up all the data required
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setName("TEST SURVEY");
    survey.setSampleValidationRules(
        new ColumnValidator[] {
          new ColumnValidator("Junk", false, new Rule[] {new MandatoryRule()})
        });
    survey.setSampleSeparator(',');
    survey.setSampleDefinitionUrl("http://junk");
    survey = surveyRepository.saveAndFlush(survey);

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setId(UUID.randomUUID());
    collectionExercise.setSurvey(survey);
    collectionExercise.setName("TEST COLLEX");
    collectionExercise.setReference("MVP012021");
    collectionExercise.setStartDate(OffsetDateTime.now());
    collectionExercise.setEndDate(OffsetDateTime.now().plusDays(2));
    collectionExercise.setMetadata(TEST_COLLECTION_EXERCISE_UPDATE_METADATA);
    collectionExercise.setCollectionInstrumentSelectionRules(
        new CollectionInstrumentSelectionRule[] {
          new CollectionInstrumentSelectionRule(0, null, "testInstrumentUrl")
        });
    collectionExercise = collectionExerciseRepository.saveAndFlush(collectionExercise);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    testCase.setSample(Map.of());
    testCase = caseRepository.saveAndFlush(testCase);

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("TEST_PACK_CODE_12343");
    emailTemplate.setTemplate(new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});
    emailTemplate.setNotifyTemplateId(UUID.randomUUID());
    emailTemplate.setDescription("Test description");
    emailTemplate = emailTemplateRepository.saveAndFlush(emailTemplate);

    FulfilmentSurveyEmailTemplate fulfilmentSurveyEmailTemplate =
        new FulfilmentSurveyEmailTemplate();
    fulfilmentSurveyEmailTemplate.setSurvey(testCase.getCollectionExercise().getSurvey());
    fulfilmentSurveyEmailTemplate.setEmailTemplate(emailTemplate);
    fulfilmentSurveyEmailTemplate.setId(UUID.randomUUID());
    fulfilmentSurveyEmailTemplateRepository.save(fulfilmentSurveyEmailTemplate);

    EventDTO emailRequestEvent = buildEventDTO(EMAIL_REQUEST_TOPIC);
    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(testCase.getId());
    emailRequest.setPackCode(emailTemplate.getPackCode());
    emailRequest.setEmail("example@example.com");

    Map<String, Object> uacMetadata = new HashMap<>();
    uacMetadata.put("wave", 1);
    emailRequest.setUacMetadata(uacMetadata);

    emailRequestEvent.getPayload().setEmailRequest(emailRequest);

    // Stub the Notify API endpoint with a success code and random response to keep the client
    // happy, this is to stop the enriched receiver from failing and nacking the resulting enriched
    // message
    NotifyApiSendEmailResponse notifyApiSendEmailResponse =
        easyRandom.nextObject(NotifyApiSendEmailResponse.class);
    String notifyApiResponseJson = objectMapper.writeValueAsString(notifyApiSendEmailResponse);
    wireMockServer.stubFor(
        WireMock.post(urlEqualTo(EMAIL_NOTIFY_API_ENDPOINT))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(201)
                    .withBody(notifyApiResponseJson)
                    .withHeader("Content-Type", "application/json")));

    // When
    pubSubHelper.publishAndConfirm(EMAIL_REQUEST_TOPIC, emailRequestEvent);

    // Then
    // Get the two expected pubsub messages
    EventDTO emailRequestEnrichedEvent;
    EventDTO enrichedEmailFulfilmentEvent;
    try (QueueSpy<EventDTO> emailRequestEnrichedQueueSpy =
        pubSubTestHelper.listen(TEST_EMAIL_REQUEST_ENRICHED_SUBSCRIPTION, EventDTO.class)) {
      emailRequestEnrichedEvent = emailRequestEnrichedQueueSpy.checkExpectedMessageReceived();
    }
    try (QueueSpy<EventDTO> enrichedEmailFulfilmentQueueSpy =
        pubSubTestHelper.listen(ENRICHED_EMAIL_FULFILMENT_SUBSCRIPTION, EventDTO.class)) {
      enrichedEmailFulfilmentEvent = enrichedEmailFulfilmentQueueSpy.checkExpectedMessageReceived();
    }

    // Check the message headers
    EventHeaderDTO emailRequestEnrichedHeader = emailRequestEnrichedEvent.getHeader();
    assertThat(emailRequestEnrichedHeader.getCorrelationId())
        .isEqualTo(emailRequestEvent.getHeader().getCorrelationId());
    assertThat(emailRequestEnrichedHeader.getSource())
        .isEqualTo(emailRequestEvent.getHeader().getSource());
    assertThat(emailRequestEnrichedHeader.getChannel())
        .isEqualTo(emailRequestEvent.getHeader().getChannel());
    assertThat(emailRequestEnrichedHeader.getOriginatingUser())
        .isEqualTo(emailRequestEvent.getHeader().getOriginatingUser());
    assertThat(emailRequestEnrichedHeader.getMessageId()).isNotNull();

    EventHeaderDTO enrichedEmailFulfilmentHeader = enrichedEmailFulfilmentEvent.getHeader();
    assertThat(enrichedEmailFulfilmentHeader.getCorrelationId())
        .isEqualTo(emailRequestEvent.getHeader().getCorrelationId());
    assertThat(enrichedEmailFulfilmentHeader.getSource())
        .isEqualTo(emailRequestEvent.getHeader().getSource());
    assertThat(enrichedEmailFulfilmentHeader.getChannel())
        .isEqualTo(emailRequestEvent.getHeader().getChannel());
    assertThat(enrichedEmailFulfilmentHeader.getOriginatingUser())
        .isEqualTo(emailRequestEvent.getHeader().getOriginatingUser());
    assertThat(enrichedEmailFulfilmentHeader.getMessageId()).isNotNull();

    // Check the message bodies
    EmailRequestEnriched emailRequestEnriched =
        emailRequestEnrichedEvent.getPayload().getEmailRequestEnriched();
    EmailConfirmation emailConfirmation =
        enrichedEmailFulfilmentEvent.getPayload().getEmailConfirmation();
    assertThat(emailRequestEnriched.getQid()).isEqualTo(emailConfirmation.getQid()).isNotEmpty();
    assertThat(emailRequestEnriched.getUac()).isEqualTo(emailConfirmation.getUac()).isNotEmpty();
    assertThat(emailConfirmation.getUacMetadata()).isNotNull();
    assertThat(emailRequestEnriched.getCaseId())
        .isEqualTo(emailConfirmation.getCaseId())
        .isEqualTo(emailRequest.getCaseId());
    assertThat(emailRequestEnriched.getPackCode())
        .isEqualTo(emailConfirmation.getPackCode())
        .isEqualTo(emailRequest.getPackCode());
    assertThat(emailRequestEnriched.getEmail()).isEqualTo(emailRequest.getEmail());
  }
}
