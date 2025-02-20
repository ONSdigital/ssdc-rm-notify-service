package uk.gov.ons.ssdc.notifysvc.messaging;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_REQUEST_PREFIX;
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
import uk.gov.ons.ssdc.common.model.entity.*;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;
import uk.gov.ons.ssdc.notifysvc.model.dto.NotifyApiSendSmsResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.repository.*;
import uk.gov.ons.ssdc.notifysvc.testUtils.PubSubTestHelper;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SmsRequestEnrichedReceiverIT {

  private static final String TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION =
      "TEST-sms-request-enriched_notify-service";

  private static final String TEST_PACK_CODE = "TEST_PACK_CODE";
  public static final String SMS_NOTIFY_API_ENDPOINT = "/v2/notifications/sms";

  private static final Map<String, String> TEST_COLLECTION_EXERCISE_UPDATE_METADATA =
      Map.of("TEST_COLLECTION_EXERCISE_UPDATE_METADATA", "TEST");

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  @Autowired private CaseRepository caseRepository;
  @Autowired private SurveyRepository surveyRepository;
  @Autowired private CollectionExerciseRepository collectionExerciseRepository;
  @Autowired private SmsTemplateRepository smsTemplateRepository;
  @Autowired private FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  @Autowired private PubSubTestHelper pubSubTestHelper;
  @Autowired private PubSubHelper pubSubHelper;

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final EasyRandom easyRandom = new EasyRandom();

  private WireMockServer wireMockServer;

  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";
  private final Map<String, String> TEST_PERSONALISATION = Map.of("name", "Mr. Test");

  @BeforeEach
  @Transactional
  public void setUp() {
    clearDownData();
    pubSubTestHelper.purgeMessages(TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION, smsRequestEnrichedTopic);
    this.wireMockServer = new WireMockServer(8089);
    wireMockServer.start();
    configureFor(wireMockServer.port());
  }

  public void clearDownData() {
    fulfilmentSurveySmsTemplateRepository.deleteAllInBatch();
    smsTemplateRepository.deleteAllInBatch();
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
  void happyPathSmsRequestEnrichedReceiver() throws JsonProcessingException, InterruptedException {
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
          new CollectionInstrumentSelectionRule(0, null, "testInstrumentUrl", null)
        });
    collectionExercise = collectionExerciseRepository.saveAndFlush(collectionExercise);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    testCase.setSample(Map.of());
    testCase = caseRepository.saveAndFlush(testCase);

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode(TEST_PACK_CODE);
    smsTemplate.setTemplate(
        new String[] {TEMPLATE_UAC_KEY, TEMPLATE_QID_KEY, TEMPLATE_REQUEST_PREFIX + "name"});
    smsTemplate.setNotifyTemplateId(UUID.randomUUID());
    smsTemplate.setDescription("Test description");
    smsTemplate.setNotifyServiceRef("test-service");
    smsTemplate = smsTemplateRepository.saveAndFlush(smsTemplate);

    FulfilmentSurveySmsTemplate fulfilmentSurveySmsTemplate = new FulfilmentSurveySmsTemplate();
    fulfilmentSurveySmsTemplate.setSurvey(testCase.getCollectionExercise().getSurvey());
    fulfilmentSurveySmsTemplate.setSmsTemplate(smsTemplate);
    fulfilmentSurveySmsTemplate.setId(UUID.randomUUID());
    fulfilmentSurveySmsTemplateRepository.saveAndFlush(fulfilmentSurveySmsTemplate);

    EventDTO smsRequestEnrichedEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequestEnriched smsRequestEnriched = new SmsRequestEnriched();
    smsRequestEnriched.setCaseId(testCase.getId());
    smsRequestEnriched.setPackCode("TEST_PACK_CODE");
    smsRequestEnriched.setUac(TEST_UAC);
    smsRequestEnriched.setQid(TEST_QID);
    smsRequestEnriched.setPersonalisation(TEST_PERSONALISATION);
    smsRequestEnriched.setPhoneNumber("07564283939");
    smsRequestEnrichedEvent.getPayload().setSmsRequestEnriched(smsRequestEnriched);

    // Stub the Notify API endpoint with a success code and random response to keep the client happy
    NotifyApiSendSmsResponse notifyApiSendSmsResponse =
        easyRandom.nextObject(NotifyApiSendSmsResponse.class);
    String notifyApiResponseJson = objectMapper.writeValueAsString(notifyApiSendSmsResponse);
    wireMockServer.stubFor(
        WireMock.post(urlEqualTo(SMS_NOTIFY_API_ENDPOINT))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(201)
                    .withBody(notifyApiResponseJson)
                    .withHeader("Content-Type", "application/json")));

    // When
    pubSubHelper.publishAndConfirm(smsRequestEnrichedTopic, smsRequestEnrichedEvent);

    Thread.sleep(1000);

    // Then
    verify(postRequestedFor(urlEqualTo(SMS_NOTIFY_API_ENDPOINT)));
  }
}
