package uk.gov.ons.ssdc.notifysvc.messaging;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_REQUEST_PREFIX;
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
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveySmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;
import uk.gov.ons.ssdc.notifysvc.model.dto.NotifyApiSendSmsResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsConfirmation;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequest;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.CollectionExerciseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SurveyRepository;
import uk.gov.ons.ssdc.notifysvc.testUtils.PubSubTestHelper;
import uk.gov.ons.ssdc.notifysvc.testUtils.QueueSpy;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SmsRequestReceiverIT {

  @Autowired private CaseRepository caseRepository;
  @Autowired private SurveyRepository surveyRepository;
  @Autowired private CollectionExerciseRepository collectionExerciseRepository;
  @Autowired private SmsTemplateRepository smsTemplateRepository;
  @Autowired private FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  @Autowired private PubSubTestHelper pubSubTestHelper;
  @Autowired private PubSubHelper pubSubHelper;

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final EasyRandom easyRandom = new EasyRandom();

  private static final String SMS_REQUEST_TOPIC = "rm-internal-sms-request";
  private static final String TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION =
      "TEST-sms-request-enriched_notify-service";

  private static final String SMS_CONFIRMATION_SUBSCRIPTION =
      "rm-internal-sms-fulfilment_notify-service-it";

  private static final Map<String, String> TEST_COLLECTION_EXERCISE_UPDATE_METADATA =
      Map.of("TEST_COLLECTION_EXERCISE_UPDATE_METADATA", "TEST");

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  @Value("${queueconfig.sms-confirmation-topic}")
  private String smsConfirmationTopic;

  public static final String SMS_NOTIFY_API_ENDPOINT = "/v2/notifications/sms";

  private WireMockServer wireMockServer;

  @BeforeEach
  @Transactional
  public void setUp() {
    clearDownData();
    pubSubTestHelper.purgeMessages(TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION, smsRequestEnrichedTopic);
    pubSubTestHelper.purgeMessages(SMS_CONFIRMATION_SUBSCRIPTION, smsConfirmationTopic);
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
  void testReceiveSmsRequest() throws InterruptedException, JsonProcessingException {
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
    smsTemplate.setPackCode("TEST_PACK_CODE");
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
    fulfilmentSurveySmsTemplateRepository.save(fulfilmentSurveySmsTemplate);

    EventDTO smsRequestEvent = buildEventDTO(SMS_REQUEST_TOPIC);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(smsTemplate.getPackCode());
    smsRequest.setPhoneNumber("07123456789");
    Map<String, String> personalisation = Map.of("name", "Mr. Test");
    smsRequest.setPersonalisation(personalisation);

    Map<String, String> uacMetadata = new HashMap<>();
    uacMetadata.put("waveOfContact", "1");
    smsRequest.setUacMetadata(uacMetadata);

    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    // Stub the Notify API endpoint with a success code and random response to keep the client
    // happy, this is to stop the enriched receiver from failing and nacking the resulting enriched
    // message
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
    pubSubHelper.publishAndConfirm(SMS_REQUEST_TOPIC, smsRequestEvent);

    // Then
    // Get the two expected pubsub messages
    EventDTO smsRequestEnrichedEvent;
    EventDTO smsConfirmationEvent;
    try (QueueSpy<EventDTO> smsRequestEnrichedQueueSpy =
        pubSubTestHelper.listen(TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION, EventDTO.class)) {
      smsRequestEnrichedEvent = smsRequestEnrichedQueueSpy.checkExpectedMessageReceived();
    }
    try (QueueSpy<EventDTO> smsConfirmationQueueSpy =
        pubSubTestHelper.listen(SMS_CONFIRMATION_SUBSCRIPTION, EventDTO.class)) {
      smsConfirmationEvent = smsConfirmationQueueSpy.checkExpectedMessageReceived();
    }

    // Check the message headers
    EventHeaderDTO smsRequestEnrichedHeader = smsRequestEnrichedEvent.getHeader();
    assertThat(smsRequestEnrichedHeader.getCorrelationId())
        .isEqualTo(smsRequestEvent.getHeader().getCorrelationId());
    assertThat(smsRequestEnrichedHeader.getSource())
        .isEqualTo(smsRequestEvent.getHeader().getSource());
    assertThat(smsRequestEnrichedHeader.getChannel())
        .isEqualTo(smsRequestEvent.getHeader().getChannel());
    assertThat(smsRequestEnrichedHeader.getOriginatingUser())
        .isEqualTo(smsRequestEvent.getHeader().getOriginatingUser());
    assertThat(smsRequestEnrichedHeader.getMessageId()).isNotNull();

    EventHeaderDTO smsConfirmationHeader = smsConfirmationEvent.getHeader();
    assertThat(smsConfirmationHeader.getCorrelationId())
        .isEqualTo(smsRequestEvent.getHeader().getCorrelationId());
    assertThat(smsConfirmationHeader.getSource())
        .isEqualTo(smsRequestEvent.getHeader().getSource());
    assertThat(smsConfirmationHeader.getChannel())
        .isEqualTo(smsRequestEvent.getHeader().getChannel());
    assertThat(smsConfirmationHeader.getOriginatingUser())
        .isEqualTo(smsRequestEvent.getHeader().getOriginatingUser());
    assertThat(smsConfirmationHeader.getMessageId()).isNotNull();

    // Check the message bodies
    SmsRequestEnriched smsRequestEnriched =
        smsRequestEnrichedEvent.getPayload().getSmsRequestEnriched();
    SmsConfirmation smsConfirmation = smsConfirmationEvent.getPayload().getSmsConfirmation();
    assertThat(smsRequestEnriched.getQid()).isEqualTo(smsConfirmation.getQid()).isNotEmpty();
    assertThat(smsRequestEnriched.getUac()).isEqualTo(smsConfirmation.getUac()).isNotEmpty();
    assertThat(smsRequestEnriched.getPersonalisation())
        .isEqualTo(smsConfirmation.getPersonalisation())
        .isEqualTo(personalisation);
    assertThat(smsConfirmation.getUacMetadata()).isNotNull();
    assertThat(smsRequestEnriched.getCaseId())
        .isEqualTo(smsConfirmation.getCaseId())
        .isEqualTo(smsRequest.getCaseId());
    assertThat(smsRequestEnriched.getPackCode())
        .isEqualTo(smsConfirmation.getPackCode())
        .isEqualTo(smsRequest.getPackCode());
    assertThat(smsRequestEnriched.getPhoneNumber()).isEqualTo(smsRequest.getPhoneNumber());
  }
}
