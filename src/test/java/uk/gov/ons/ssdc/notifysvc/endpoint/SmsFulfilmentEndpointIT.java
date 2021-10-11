package uk.gov.ons.ssdc.notifysvc.endpoint;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_UAC_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveySmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;
import uk.gov.ons.ssdc.notifysvc.model.dto.NotifyApiResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.CollectionExerciseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveySmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SurveyRepository;
import uk.gov.ons.ssdc.notifysvc.testUtils.PubSubTestHelper;
import uk.gov.ons.ssdc.notifysvc.testUtils.QueueSpy;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SmsFulfilmentEndpointIT {

  private static final String VALID_PHONE_NUMBER = "07123456789";
  private static final String TEST_PACK_CODE = "TEST_PACK_CODE";
  private static final String SMS_FULFILMENT_ENDPOINT = "/sms-fulfilment";
  public static final String SMS_NOTIFY_API_ENDPOINT = "/v2/notifications/sms";
  private static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");
  private static final Map<String, String> TEST_COLLECTION_EXERCISE_METADATA =
      Map.of("TEST_COLLECTION_EXERCISE_METADATA", "TEST");

  private static final String ENRICHED_SMS_FULFILMENT_SUBSCRIPTION =
      "rm-internal-sms-fulfilment_notify-service-it";

  @Value("${queueconfig.sms-fulfilment-topic}")
  private String smsFulfilmentTopic;

  @Autowired private CaseRepository caseRepository;
  @Autowired private SurveyRepository surveyRepository;
  @Autowired private CollectionExerciseRepository collectionExerciseRepository;
  @Autowired private SmsTemplateRepository smsTemplateRepository;
  @Autowired private FulfilmentSurveySmsTemplateRepository fulfilmentSurveySmsTemplateRepository;
  @Autowired private PubSubTestHelper pubSubTestHelper;
  @LocalServerPort private int port;

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final EasyRandom easyRandom = new EasyRandom();

  private WireMockServer wireMockServer;

  @BeforeEach
  @Transactional
  public void setUp() {
    clearDownData();
    pubSubTestHelper.purgeMessages(ENRICHED_SMS_FULFILMENT_SUBSCRIPTION, smsFulfilmentTopic);
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
  }

  @Test
  void testSmsFulfilment() throws JsonProcessingException, InterruptedException {
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
    survey = surveyRepository.saveAndFlush(survey);

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setId(UUID.randomUUID());
    collectionExercise.setSurvey(survey);
    collectionExercise.setName("TEST COLLEX");
    collectionExercise.setReference("MVP012021");
    collectionExercise.setStartDate(OffsetDateTime.now());
    collectionExercise.setEndDate(OffsetDateTime.now().plusDays(2));
    collectionExercise.setMetadata(TEST_COLLECTION_EXERCISE_METADATA);
    collectionExercise = collectionExerciseRepository.saveAndFlush(collectionExercise);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    testCase.setSample(Map.of());
    testCase = caseRepository.saveAndFlush(testCase);

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode(TEST_PACK_CODE);
    smsTemplate.setTemplate(new String[] {SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY});
    smsTemplate.setNotifyTemplateId(UUID.randomUUID());
    smsTemplate = smsTemplateRepository.saveAndFlush(smsTemplate);

    FulfilmentSurveySmsTemplate fulfilmentSurveySmsTemplate = new FulfilmentSurveySmsTemplate();
    fulfilmentSurveySmsTemplate.setSurvey(testCase.getCollectionExercise().getSurvey());
    fulfilmentSurveySmsTemplate.setSmsTemplate(smsTemplate);
    fulfilmentSurveySmsTemplate.setId(UUID.randomUUID());
    fulfilmentSurveySmsTemplateRepository.saveAndFlush(fulfilmentSurveySmsTemplate);

    // Build the event JSON to post in
    RequestDTO smsFulfilmentEvent = new RequestDTO();
    RequestHeaderDTO header = new RequestHeaderDTO();
    header.setSource("TEST_SOURCE");
    header.setChannel("TEST_CHANNEL");
    header.setCorrelationId(UUID.randomUUID());
    header.setOriginatingUser("TEST_USER");

    RequestPayloadDTO payload = new RequestPayloadDTO();
    SmsFulfilment smsFulfilment = new SmsFulfilment();
    smsFulfilment.setCaseId(testCase.getId());
    smsFulfilment.setPackCode(smsTemplate.getPackCode());
    smsFulfilment.setPhoneNumber(VALID_PHONE_NUMBER);
    smsFulfilment.setUacMetadata(TEST_UAC_METADATA);

    smsFulfilmentEvent.setHeader(header);
    payload.setSmsFulfilment(smsFulfilment);
    smsFulfilmentEvent.setPayload(payload);

    // Stub the Notify API endpoint with a success code and random response to keep the client happy
    NotifyApiResponse notifyApiResponse = easyRandom.nextObject(NotifyApiResponse.class);
    String notifyApiResponseJson = objectMapper.writeValueAsString(notifyApiResponse);
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlEqualTo(SMS_NOTIFY_API_ENDPOINT))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(201)
                    .withBody(notifyApiResponseJson)
                    .withHeader("Content-Type", "application/json")));

    // Build the SMS fulfilment request
    RestTemplate restTemplate = new RestTemplate();
    String url = "http://localhost:" + port + SMS_FULFILMENT_ENDPOINT;
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request =
        new HttpEntity<>(objectMapper.writeValueAsString(smsFulfilmentEvent), headers);

    // When
    // We post in the SMS fulfilment request
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode responseJson = objectMapper.readTree(response.getBody());
    assertThat(responseJson.get("uacHash").textValue()).isNotEmpty();
    assertThat(responseJson.get("qid").textValue()).isNotEmpty();

    // Listen to the test subscription to receive and inspect the resulting enriched event message
    EventDTO actualEnrichedEvent;
    try (QueueSpy<EventDTO> smsFulfilmentQueueSpy =
        pubSubTestHelper.listen(ENRICHED_SMS_FULFILMENT_SUBSCRIPTION, EventDTO.class)) {
      // Check the outbound event is received and correct
      actualEnrichedEvent = smsFulfilmentQueueSpy.checkExpectedMessageReceived();
    }

    assertThat(actualEnrichedEvent.getHeader().getTopic()).isEqualTo(smsFulfilmentTopic);
    assertThat(actualEnrichedEvent.getHeader().getCorrelationId())
        .isEqualTo(smsFulfilmentEvent.getHeader().getCorrelationId());

    assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getCaseId())
        .isEqualTo(testCase.getId());
    assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getPackCode())
        .isEqualTo(smsFulfilment.getPackCode());
    assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getUacMetadata())
        .isEqualTo(smsFulfilment.getUacMetadata());
    assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getUac()).isNotEmpty();
    assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getQid()).isNotEmpty();

    // Check the Notify API stub was indeed called
    verify(postRequestedFor(urlEqualTo(SMS_NOTIFY_API_ENDPOINT)));
  }
}
