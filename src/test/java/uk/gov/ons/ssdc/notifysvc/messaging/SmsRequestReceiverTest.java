package uk.gov.ons.ssdc.notifysvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.buildEventDTO;
import static uk.gov.ons.ssdc.notifysvc.testUtils.MessageConstructor.constructMessageWithValidTimeStamp;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_QID_KEY;
import static uk.gov.ons.ssdc.notifysvc.utils.Constants.SMS_TEMPLATE_UAC_KEY;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequest;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.SmsRequestEnriched;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.SmsRequestService;
import uk.gov.ons.ssdc.notifysvc.utils.PubSubHelper;

@ExtendWith(MockitoExtension.class)
class SmsRequestReceiverTest {

  @Mock SmsTemplateRepository smsTemplateRepository;
  @Mock CaseRepository caseRepository;
  @Mock SmsRequestService smsRequestService;
  @Mock PubSubHelper pubSubHelper;

  @InjectMocks SmsRequestReceiver smsRequestReceiver;

  @Value("${queueconfig.sms-request-enriched-topic}")
  private String smsRequestEnrichedTopic;

  private final String TEST_PACK_CODE = "TEST_PACK_CODE";
  private final String TEST_UAC = "TEST_UAC";
  private final String TEST_QID = "TEST_QID";

  @Test
  void testReceiveMessageHappyPathWithUacQid() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {SMS_TEMPLATE_QID_KEY, SMS_TEMPLATE_UAC_KEY});

    UacQidCreatedPayloadDTO newUacQidCreated = new UacQidCreatedPayloadDTO();
    newUacQidCreated.setUac(TEST_UAC);
    newUacQidCreated.setQid(TEST_QID);

    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsRequestService.fetchNewUacQidPairIfRequired(smsTemplate.getTemplate()))
        .thenReturn(newUacQidCreated);

    EventDTO smsRequestEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(TEST_PACK_CODE);
    smsRequest.setPhoneNumber("07123456789");
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When
    smsRequestReceiver.receiveMessage(eventMessage);

    // Then
    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper)
        .publishAndConfirm(eq(smsRequestEnrichedTopic), eventDTOArgumentCaptor.capture());
    EventDTO sentEvent = eventDTOArgumentCaptor.getValue();
    assertThat(sentEvent.getHeader().getCorrelationId())
        .isEqualTo(smsRequestEvent.getHeader().getCorrelationId());
    SmsRequestEnriched smsRequestEnriched = sentEvent.getPayload().getSmsRequestEnriched();
    assertThat(smsRequestEnriched.getPackCode()).isEqualTo(smsRequest.getPackCode());
    assertThat(smsRequestEnriched.getCaseId()).isEqualTo(smsRequest.getCaseId());
    assertThat(smsRequestEnriched.getPhoneNumber()).isEqualTo(smsRequest.getPhoneNumber());
    assertThat(smsRequestEnriched.getUac()).isEqualTo(newUacQidCreated.getUac());
    assertThat(smsRequestEnriched.getQid()).isEqualTo(newUacQidCreated.getQid());

    verify(smsRequestService)
        .buildAndSendEnrichedSmsFulfilment(
            testCase.getId(),
            smsTemplate.getPackCode(),
            newUacQidCreated,
            smsRequestEvent.getHeader().getSource(),
            smsRequestEvent.getHeader().getChannel(),
            smsRequestEvent.getHeader().getCorrelationId(),
            smsRequestEvent.getHeader().getOriginatingUser());
  }

  @Test
  void testReceiveMessageHappyPathWithoutUacQid() {
    // Given
    Case testCase = new Case();
    testCase.setId(UUID.randomUUID());

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("TEST_PACK_CODE");
    smsTemplate.setTemplate(new String[] {"foobar"});

    when(smsTemplateRepository.findById(smsTemplate.getPackCode()))
        .thenReturn(Optional.of(smsTemplate));
    when(caseRepository.findById(testCase.getId())).thenReturn(Optional.of(testCase));
    when(smsRequestService.fetchNewUacQidPairIfRequired(smsTemplate.getTemplate()))
        .thenReturn(null);

    EventDTO smsRequestEvent = buildEventDTO(smsRequestEnrichedTopic);
    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(testCase.getId());
    smsRequest.setPackCode(TEST_PACK_CODE);
    smsRequest.setPhoneNumber("07123456789");
    smsRequestEvent.getPayload().setSmsRequest(smsRequest);

    Message<byte[]> eventMessage = constructMessageWithValidTimeStamp(smsRequestEvent);

    // When
    smsRequestReceiver.receiveMessage(eventMessage);

    // Then
    ArgumentCaptor<EventDTO> eventDTOArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(pubSubHelper)
        .publishAndConfirm(eq(smsRequestEnrichedTopic), eventDTOArgumentCaptor.capture());
    EventDTO sentEvent = eventDTOArgumentCaptor.getValue();
    assertThat(sentEvent.getHeader().getCorrelationId())
        .isEqualTo(smsRequestEvent.getHeader().getCorrelationId());
    SmsRequestEnriched smsRequestEnriched = sentEvent.getPayload().getSmsRequestEnriched();
    assertThat(smsRequestEnriched.getPackCode()).isEqualTo(smsRequest.getPackCode());
    assertThat(smsRequestEnriched.getCaseId()).isEqualTo(smsRequest.getCaseId());
    assertThat(smsRequestEnriched.getPhoneNumber()).isEqualTo(smsRequest.getPhoneNumber());
    assertThat(smsRequestEnriched.getUac()).isNull();
    assertThat(smsRequestEnriched.getQid()).isNull();

    verify(smsRequestService)
        .buildAndSendEnrichedSmsFulfilment(
            testCase.getId(),
            smsTemplate.getPackCode(),
            null,
            smsRequestEvent.getHeader().getSource(),
            smsRequestEvent.getHeader().getChannel(),
            smsRequestEvent.getHeader().getCorrelationId(),
            smsRequestEvent.getHeader().getOriginatingUser());
  }
}
