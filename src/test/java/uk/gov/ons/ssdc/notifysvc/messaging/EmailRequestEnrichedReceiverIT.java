package uk.gov.ons.ssdc.notifysvc.messaging;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.OffsetDateTime;
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
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveyEmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;
import uk.gov.ons.ssdc.notifysvc.model.dto.NotifyApiSendSmsResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EmailRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.CollectionExerciseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveyEmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SurveyRepository;
import uk.gov.ons.ssdc.notifysvc.testUtils.PubSubTestHelper;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmailRequestEnrichedReceiverIT {

  private static final String TEST_EMAIl_REQUEST_ENRICHED_SUBSCRIPTION =
      "TEST-email-request-enriched_notify-service";

  private static final String TEST_PACK_CODE = "TEST_PACK_CODE";
  public static final String EMAIL_NOTIFY_API_ENDPOINT = "/v2/notifications/email";

  private static final Map<String, String> TEST_COLLECTION_EXERCISE_UPDATE_METADATA =
      Map.of("TEST_COLLECTION_EXERCISE_UPDATE_METADATA", "TEST");

  @Value("${queueconfig.email-request-enriched-topic}")
  private String emailRequestEnrichedTopic;

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

  private WireMockServer wireMockServer;

  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";

  @BeforeEach
  @Transactional
  public void setUp() {
    clearDownData();
    pubSubTestHelper.purgeMessages(
        TEST_EMAIl_REQUEST_ENRICHED_SUBSCRIPTION, emailRequestEnrichedTopic);
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
  void happyPathEmailRequestEnrichedReceiver()
      throws JsonProcessingException, InterruptedException {
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
    collectionExercise = collectionExerciseRepository.saveAndFlush(collectionExercise);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    testCase.setSample(Map.of());
    testCase = caseRepository.saveAndFlush(testCase);

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode(TEST_PACK_CODE);
    emailTemplate.setTemplate(new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY});
    emailTemplate.setNotifyTemplateId(UUID.randomUUID());
    emailTemplate.setDescription("Test description");
    emailTemplate = emailTemplateRepository.saveAndFlush(emailTemplate);

    FulfilmentSurveyEmailTemplate fulfilmentSurveyEmailTemplate =
        new FulfilmentSurveyEmailTemplate();
    fulfilmentSurveyEmailTemplate.setSurvey(testCase.getCollectionExercise().getSurvey());
    fulfilmentSurveyEmailTemplate.setEmailTemplate(emailTemplate);
    fulfilmentSurveyEmailTemplate.setId(UUID.randomUUID());
    fulfilmentSurveyEmailTemplateRepository.saveAndFlush(fulfilmentSurveyEmailTemplate);

    EventDTO emailRequestEnrichedEvent = buildEventDTO(emailRequestEnrichedTopic);
    EmailRequestEnriched emailRequestEnriched = new EmailRequestEnriched();
    emailRequestEnriched.setCaseId(testCase.getId());
    emailRequestEnriched.setPackCode("TEST_PACK_CODE");
    emailRequestEnriched.setUac(TEST_UAC);
    emailRequestEnriched.setQid(TEST_QID);
    emailRequestEnriched.setEmail("example@example.com");
    emailRequestEnrichedEvent.getPayload().setEmailRequestEnriched(emailRequestEnriched);

    // Stub the Notify API endpoint with a success code and random response to keep the client happy
    NotifyApiSendSmsResponse notifyApiSendSmsResponse = easyRandom.nextObject(NotifyApiSendSmsResponse.class);
    String notifyApiResponseJson = objectMapper.writeValueAsString(notifyApiSendSmsResponse);
    wireMockServer.stubFor(
        WireMock.post(urlEqualTo(EMAIL_NOTIFY_API_ENDPOINT))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(201)
                    .withBody(notifyApiResponseJson)
                    .withHeader("Content-Type", "application/json")));

    pubSubHelper.publishAndConfirm(emailRequestEnrichedTopic, emailRequestEnrichedEvent);

    Thread.sleep(1000);
    verify(postRequestedFor(urlEqualTo(EMAIL_NOTIFY_API_ENDPOINT)));
  }
}
