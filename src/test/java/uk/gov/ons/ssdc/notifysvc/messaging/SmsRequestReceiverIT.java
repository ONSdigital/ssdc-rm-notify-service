package uk.gov.ons.ssdc.notifysvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_UAC_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveySmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EnrichedSmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
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

  private static final String ENRICHED_SMS_FULFILMENT_SUBSCRIPTION =
      "rm-internal-sms-fulfilment_notify-service-it";

  @BeforeEach
  @Transactional
  public void setUp() {
    clearDownData();
  }

  public void clearDownData() {
    fulfilmentSurveySmsTemplateRepository.deleteAllInBatch();
    smsTemplateRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
    collectionExerciseRepository.deleteAllInBatch();
    surveyRepository.deleteAllInBatch();
  }

  @Test
  void testReceiveSmsRequest() throws InterruptedException {
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
    collectionExercise = collectionExerciseRepository.saveAndFlush(collectionExercise);

    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());
    testCase.setCollectionExercise(collectionExercise);
    testCase.setSample(Map.of());
    testCase = caseRepository.saveAndFlush(testCase);

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST");
    smsTemplate.setTemplate(new String[] {SMS_TEMPLATE_UAC_KEY, SMS_TEMPLATE_QID_KEY});
    smsTemplate.setNotifyTemplateId(UUID.randomUUID());
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
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    // When
    try (QueueSpy<EventDTO> smsRequestEnrichedQueueSpy =
            pubSubTestHelper.listen(TEST_SMS_REQUEST_ENRICHED_SUBSCRIPTION, EventDTO.class);
        QueueSpy<EventDTO> enrichedSmsFulfilmentQueueSpy =
            pubSubTestHelper.listen(ENRICHED_SMS_FULFILMENT_SUBSCRIPTION, EventDTO.class)) {
      pubSubHelper.publishAndConfirm(SMS_REQUEST_TOPIC, smsRequestEvent);

      // Then
      // Get the two expected pubsub messages
      EventDTO smsRequestEnrichedEvent = smsRequestEnrichedQueueSpy.checkExpectedMessageReceived();
      EventDTO enrichedSmsFulfilmentEvent =
          enrichedSmsFulfilmentQueueSpy.checkExpectedMessageReceived();

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

      EventHeaderDTO enrichedSmsFulfilmentHeader = enrichedSmsFulfilmentEvent.getHeader();
      assertThat(enrichedSmsFulfilmentHeader.getCorrelationId())
          .isEqualTo(smsRequestEvent.getHeader().getCorrelationId());
      assertThat(enrichedSmsFulfilmentHeader.getSource())
          .isEqualTo(smsRequestEvent.getHeader().getSource());
      assertThat(enrichedSmsFulfilmentHeader.getChannel())
          .isEqualTo(smsRequestEvent.getHeader().getChannel());
      assertThat(enrichedSmsFulfilmentHeader.getOriginatingUser())
          .isEqualTo(smsRequestEvent.getHeader().getOriginatingUser());
      assertThat(enrichedSmsFulfilmentHeader.getMessageId()).isNotNull();

      // Check the message bodies
      SmsRequestEnriched smsRequestEnriched =
          smsRequestEnrichedEvent.getPayload().getSmsRequestEnriched();
      EnrichedSmsFulfilment enrichedSmsFulfilment =
          enrichedSmsFulfilmentEvent.getPayload().getEnrichedSmsFulfilment();
      assertThat(smsRequestEnriched.getQid())
          .isEqualTo(enrichedSmsFulfilment.getQid())
          .isNotEmpty();
      assertThat(smsRequestEnriched.getUac())
          .isEqualTo(enrichedSmsFulfilment.getUac())
          .isNotEmpty();
      assertThat(smsRequestEnriched.getCaseId())
          .isEqualTo(enrichedSmsFulfilment.getCaseId())
          .isEqualTo(smsRequest.getCaseId());
      assertThat(smsRequestEnriched.getPackCode())
          .isEqualTo(enrichedSmsFulfilment.getPackCode())
          .isEqualTo(smsRequest.getPackCode());
      assertThat(smsRequestEnriched.getPhoneNumber()).isEqualTo(smsRequest.getPhoneNumber());
    }
  }
}
