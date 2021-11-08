package uk.gov.ons.ssdc.notifysvc.endpoint;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.TEMPLATE_UAC_KEY;

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
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveyEmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;
import uk.gov.ons.ssdc.notifysvc.model.dto.NotifyApiSendEmailResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.EmailFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.CollectionExerciseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.FulfilmentSurveyEmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SurveyRepository;
import uk.gov.ons.ssdc.notifysvc.testUtils.PubSubTestHelper;
import uk.gov.ons.ssdc.notifysvc.testUtils.QueueSpy;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmailFulfilmentEndpointIT {

  private static final String VALID_EMAIL_ADDRESS = "example@example.com";
  private static final String TEST_PACK_CODE = "TEST_PACK_CODE";
  private static final String EMAIL_FULFILMENT_ENDPOINT = "/email-fulfilment";
  public static final String EMAIL_NOTIFY_API_ENDPOINT = "/v2/notifications/email";
  private static final Map<String, String> TEST_UAC_METADATA = Map.of("TEST_UAC_METADATA", "TEST");
  private static final Map<String, String> TEST_COLLECTION_EXERCISE_UPDATE_METADATA =
      Map.of("TEST_COLLECTION_EXERCISE_UPDATE_METADATA", "TEST");

  private static final String ENRICHED_EMAIL_FULFILMENT_SUBSCRIPTION =
      "rm-internal-email-fulfilment_notify-service-it";

  @Value("${queueconfig.email-fulfilment-topic}")
  private String emailFulfilmentTopic;

  @Autowired private CaseRepository caseRepository;
  @Autowired private SurveyRepository surveyRepository;
  @Autowired private CollectionExerciseRepository collectionExerciseRepository;
  @Autowired private EmailTemplateRepository emailTemplateRepository;

  @Autowired
  private FulfilmentSurveyEmailTemplateRepository fulfilmentSurveyEmailTemplateRepository;

  @Autowired private PubSubTestHelper pubSubTestHelper;
  @LocalServerPort private int port;

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final EasyRandom easyRandom = new EasyRandom();

  private WireMockServer wireMockServer;

  @BeforeEach
  @Transactional
  public void setUp() {
    clearDownData();
    pubSubTestHelper.purgeMessages(ENRICHED_EMAIL_FULFILMENT_SUBSCRIPTION, emailFulfilmentTopic);
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
  void testEmailFulfilment() throws JsonProcessingException, InterruptedException {
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

    // Build the event JSON to post in
    RequestDTO emailFulfilmentEvent = new RequestDTO();
    RequestHeaderDTO header = new RequestHeaderDTO();
    header.setSource("TEST_SOURCE");
    header.setChannel("TEST_CHANNEL");
    header.setCorrelationId(UUID.randomUUID());
    header.setOriginatingUser("TEST_USER");

    RequestPayloadDTO payload = new RequestPayloadDTO();
    EmailFulfilment emailFulfilment = new EmailFulfilment();
    emailFulfilment.setCaseId(testCase.getId());
    emailFulfilment.setPackCode(emailTemplate.getPackCode());
    emailFulfilment.setEmail(VALID_EMAIL_ADDRESS);
    emailFulfilment.setUacMetadata(TEST_UAC_METADATA);

    emailFulfilmentEvent.setHeader(header);
    payload.setEmailFulfilment(emailFulfilment);
    emailFulfilmentEvent.setPayload(payload);

    // Stub the Notify API endpoint with a success code and random response to keep the client happy
    NotifyApiSendEmailResponse notifyApiSendEmailResponse =
        easyRandom.nextObject(NotifyApiSendEmailResponse.class);
    String notifyApiResponseJson = objectMapper.writeValueAsString(notifyApiSendEmailResponse);
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlEqualTo(EMAIL_NOTIFY_API_ENDPOINT))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(201)
                    .withBody(notifyApiResponseJson)
                    .withHeader("Content-Type", "application/json")));

    // Build the email fulfilment request
    RestTemplate restTemplate = new RestTemplate();
    String url = "http://localhost:" + port + EMAIL_FULFILMENT_ENDPOINT;
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> request =
        new HttpEntity<>(objectMapper.writeValueAsString(emailFulfilmentEvent), headers);

    // When
    // We post in the email fulfilment request
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode responseJson = objectMapper.readTree(response.getBody());
    assertThat(responseJson.get("uacHash").textValue()).isNotEmpty();
    assertThat(responseJson.get("qid").textValue()).isNotEmpty();

    // Listen to the test subscription to receive and inspect the resulting enriched event message
    EventDTO actualEnrichedEvent;
    try (QueueSpy<EventDTO> emailFulfilmentQueueSpy =
        pubSubTestHelper.listen(ENRICHED_EMAIL_FULFILMENT_SUBSCRIPTION, EventDTO.class)) {
      // Check the outbound event is received and correct
      actualEnrichedEvent = emailFulfilmentQueueSpy.checkExpectedMessageReceived();
    }

    assertThat(actualEnrichedEvent.getHeader().getTopic()).isEqualTo(emailFulfilmentTopic);
    assertThat(actualEnrichedEvent.getHeader().getCorrelationId())
        .isEqualTo(emailFulfilmentEvent.getHeader().getCorrelationId());

    assertThat(actualEnrichedEvent.getPayload().getEnrichedEmailFulfilment().getCaseId())
        .isEqualTo(testCase.getId());
    assertThat(actualEnrichedEvent.getPayload().getEnrichedEmailFulfilment().getPackCode())
        .isEqualTo(emailFulfilment.getPackCode());
    assertThat(actualEnrichedEvent.getPayload().getEnrichedEmailFulfilment().getUacMetadata())
        .isEqualTo(emailFulfilment.getUacMetadata());
    assertThat(actualEnrichedEvent.getPayload().getEnrichedEmailFulfilment().getUac()).isNotEmpty();
    assertThat(actualEnrichedEvent.getPayload().getEnrichedEmailFulfilment().getQid()).isNotEmpty();

    // Check the Notify API stub was indeed called
    verify(postRequestedFor(urlEqualTo(EMAIL_NOTIFY_API_ENDPOINT)));
  }
}
