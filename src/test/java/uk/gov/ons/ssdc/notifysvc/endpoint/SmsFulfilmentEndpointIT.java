package uk.gov.ons.ssdc.notifysvc.endpoint;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import uk.gov.ons.ssdc.notifysvc.model.dto.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.NotifyApiResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.entity.Case;
import uk.gov.ons.ssdc.notifysvc.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.notifysvc.model.entity.FulfilmentSurveySmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.entity.Survey;
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

  private static final String SMS_FULFILMENT_TEST_SUBSCRIPTION =
      "rm-internal-sms-fulfilment_notify-service-it";

  private static final String SMS_TEMPLATE_UAC_KEY = "__uac__";
  private static final String SMS_TEMPLATE_QID_KEY = "__qid__";
  private static final String SMS_FULFILMENT_ENDPOINT = "/sms-fulfilment";
  private static final String VALID_PHONE_NUMBER = "07123456789";
  public static final String SMS_NOTIFY_API_ENDPOINT = "/v2/notifications/sms";

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
    pubSubTestHelper.purgeMessages(SMS_FULFILMENT_TEST_SUBSCRIPTION, smsFulfilmentTopic);
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
  void smsFulfilment() throws JsonProcessingException, InterruptedException {
    // Given
    // Set up all the data required
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setName("TEST SURVEY");
    survey.setSampleValidationRules("[]");
    survey.setSampleSeparator(',');
    survey = surveyRepository.saveAndFlush(survey);

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setId(UUID.randomUUID());
    collectionExercise.setSurvey(survey);
    collectionExercise.setName("TEST COLLEX");
    collectionExercise = collectionExerciseRepository.saveAndFlush(collectionExercise);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    testCase.setSample(Map.of());
    testCase = caseRepository.saveAndFlush(testCase);

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST");
    smsTemplate.setTemplate(new String[] {SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY});
    smsTemplate.setNotifyId(UUID.randomUUID());
    smsTemplate = smsTemplateRepository.saveAndFlush(smsTemplate);

    FulfilmentSurveySmsTemplate fulfilmentSurveySmsTemplate = new FulfilmentSurveySmsTemplate();
    fulfilmentSurveySmsTemplate.setSurvey(testCase.getCollectionExercise().getSurvey());
    fulfilmentSurveySmsTemplate.setSmsTemplate(smsTemplate);
    fulfilmentSurveySmsTemplate.setId(UUID.randomUUID());
    fulfilmentSurveySmsTemplateRepository.save(fulfilmentSurveySmsTemplate);

    // Build the event JSON to post in
    EventDTO smsFulfilmentEvent = new EventDTO();
    EventHeaderDTO event = new EventHeaderDTO();
    PayloadDTO payloadDTO = new PayloadDTO();
    SmsFulfilment smsFulfilment = new SmsFulfilment();
    smsFulfilment.setCaseId(testCase.getId());
    smsFulfilment.setPackCode(smsTemplate.getPackCode());
    smsFulfilment.setPhoneNumber(VALID_PHONE_NUMBER);

    smsFulfilmentEvent.setHeader(event);
    payloadDTO.setSmsFulfilment(smsFulfilment);
    smsFulfilmentEvent.setPayload(payloadDTO);

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

    // Listen to the test subscription to receive and inspect the resulting enriched event message
    try (QueueSpy<EventDTO> smsFulfilmentQueueSpy =
        pubSubTestHelper.listen(SMS_FULFILMENT_TEST_SUBSCRIPTION, EventDTO.class)) {

      // When
      // We post in the SMS fulfilment request
      ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Check the outbound event is received and correct
      EventDTO actualEnrichedEvent = smsFulfilmentQueueSpy.checkExpectedMessageReceived();

      assertThat(actualEnrichedEvent.getHeader().getTopic()).isEqualTo(smsFulfilmentTopic);
      assertThat(actualEnrichedEvent.getHeader().getCorrelationId())
          .isEqualTo(smsFulfilmentEvent.getHeader().getCorrelationId());
      assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getCaseId())
          .isEqualTo(testCase.getId());
      assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getPackCode())
          .isEqualTo(smsFulfilment.getPackCode());
      assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getUac()).isNotEmpty();
      assertThat(actualEnrichedEvent.getPayload().getEnrichedSmsFulfilment().getQid()).isNotEmpty();
    }

    // Check the Notify API stub was indeed called
    verify(1, postRequestedFor(urlEqualTo(SMS_NOTIFY_API_ENDPOINT)));
  }
}
