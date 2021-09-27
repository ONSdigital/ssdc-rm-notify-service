package uk.gov.ons.ssdc.notifysvc.messaging;

import static java.util.Map.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.constructMessageWithValidTimeStamp;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_UAC_KEY;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.SmsRequestService;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

@ExtendWith(MockitoExtension.class)
class SmsRequestEnrichedReceiverTest {
  @Mock SmsTemplateRepository smsTemplateRepository;
  @Mock CaseRepository caseRepository;
  @Mock SmsRequestService smsRequestService;
  @Mock NotificationClientApi notificationClientApi;

  @InjectMocks SmsRequestEnrichedReceiver smsRequestEnrichedReceiver;

  @Value("${notify.senderId}")
  private String senderId;

  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  @Test
  void testReceiveMessageHappyPath() throws NotificationClientException {

    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {SMS_TEMPLATE_QID_KEY, SMS_TEMPLATE_UAC_KEY});
    smsTemplate.setNotifyTemplateId(UUID.randomUUID());

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    EventDTO smsRequestEnrichedEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequestEnriched smsRequestEnriched = new SmsRequestEnriched();
    smsRequestEnriched.setCaseId(testCase.getId());
    smsRequestEnriched.setPackCode("TEST_PACK_CODE");
    smsRequestEnriched.setUac(TEST_UAC);
    smsRequestEnriched.setQid(TEST_QID);
    smsRequestEnriched.setPhoneNumber("07564283939");
    smsRequestEnrichedEvent.getPayload().setSmsRequestEnriched(smsRequestEnriched);

    Map<String, String> personalisationValues =
        Map.ofEntries(entry(SMS_TEMPLATE_UAC_KEY, TEST_UAC), entry(SMS_TEMPLATE_QID_KEY, TEST_QID));

    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsRequestService.buildPersonalisationFromTemplate(
            smsTemplate, testCase, smsRequestEnriched.getUac(), smsRequestEnriched.getQid()))
        .thenReturn(personalisationValues);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEnrichedEvent);

    // When
    smsRequestEnrichedReceiver.receiveMessage(eventMessage);

    // Then
    verify(notificationClientApi)
        .sendSms(
            smsTemplate.getNotifyTemplateId().toString(),
            smsRequestEnrichedEvent.getPayload().getSmsRequestEnriched().getPhoneNumber(),
            personalisationValues,
            senderId);
  }
}