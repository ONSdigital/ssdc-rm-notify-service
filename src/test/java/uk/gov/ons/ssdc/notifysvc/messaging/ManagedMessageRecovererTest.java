package uk.gov.ons.ssdc.notifysvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.retry.RetryContext;
import uk.gov.ons.ssdc.notifysvc.client.ExceptionManagerClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.ExceptionReportResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SkippedMessage;

@ExtendWith(MockitoExtension.class)
class ManagedMessageRecovererTest {
  private static String TEST_MESSAGE_HASH =
      "90f56b5b3ffe9558a546af25a7256da4b2761864575f9d59c81b70629023465b";

  @Mock private BasicAcknowledgeablePubsubMessage originalMessage;

  @Mock private ExceptionManagerClient exceptionManagerClient;

  @InjectMocks private ManagedMessageRecoverer underTest;

  @Test
  public void testRecover() throws Exception {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    // When
    underTest.recover(retryContext);

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq("Notify Service"),
            eq("TEST SUBSCRIPTION"),
            any(Throwable.class),
            anyString());
    verify(originalMessage).nack();
    verify(originalMessage, never()).ack();
  }

  @Test
  public void testRecoverLogIt() throws Exception {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setLogIt(true);
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    // When
    underTest.recover(retryContext);

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq("Notify Service"),
            eq("TEST SUBSCRIPTION"),
            any(RuntimeException.class),
            contains(
                "uk.gov.ons.ssdc.notifysvc.messaging.ManagedMessageRecovererTest.testSetupTestRecover"));
    verify(originalMessage).nack();
    verify(originalMessage, never()).ack();
  }

  @Test
  public void testRecoverSkip() throws Exception {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setSkipIt(true);
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    // When
    underTest.recover(retryContext);

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq("Notify Service"),
            eq("TEST SUBSCRIPTION"),
            any(RuntimeException.class),
            contains(
                "uk.gov.ons.ssdc.notifysvc.messaging.ManagedMessageRecovererTest.testSetupTestRecover"));
    verify(originalMessage, never()).nack();
    verify(originalMessage, never()).ack(); // No exception thrown = recover ack

    ArgumentCaptor<SkippedMessage> skippedMessageArgCapt =
        ArgumentCaptor.forClass(SkippedMessage.class);
    verify(exceptionManagerClient).storeMessageBeforeSkipping(skippedMessageArgCapt.capture());
    SkippedMessage skippedMessage = skippedMessageArgCapt.getValue();
    assertThat(skippedMessage.getMessageHash()).isEqualTo(TEST_MESSAGE_HASH);
    assertThat(skippedMessage.getMessagePayload()).isEqualTo("TEST PAYLOAD".getBytes());
    assertThat(skippedMessage.getSubscription()).isEqualTo("TEST SUBSCRIPTION");
    assertThat(skippedMessage.getService()).isEqualTo("Notify Service");
  }

  @Test
  public void testRecoverSkipFailureDoesNotAck() throws Exception {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setSkipIt(true);
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    doThrow(RuntimeException.class)
        .when(exceptionManagerClient)
        .storeMessageBeforeSkipping(any(SkippedMessage.class));

    // When
    underTest.recover(retryContext);

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq("Notify Service"),
            eq("TEST SUBSCRIPTION"),
            any(RuntimeException.class),
            contains(
                "uk.gov.ons.ssdc.notifysvc.messaging.ManagedMessageRecovererTest.testSetupTestRecover"));
    verify(originalMessage).nack();
    verify(originalMessage, never()).ack();
  }

  @Test
  public void testRecoverPeek() throws Exception {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setPeek(true);
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    // When
    underTest.recover(retryContext);

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq("Notify Service"),
            eq("TEST SUBSCRIPTION"),
            any(RuntimeException.class),
            contains(
                "uk.gov.ons.ssdc.notifysvc.messaging.ManagedMessageRecovererTest.testSetupTestRecover"));
    verify(originalMessage).nack();
    verify(originalMessage, never()).ack();

    verify(exceptionManagerClient).respondToPeek(TEST_MESSAGE_HASH, "TEST PAYLOAD".getBytes());
  }

  private RetryContext testSetupTestRecover(ExceptionReportResponse exceptionReportResponse) {
    MessagingException messagingException = mock(MessagingException.class);
    when(messagingException.getCause())
        .thenReturn(new RuntimeException(new RuntimeException("TEST EXCEPTION")));

    RetryContext retryContext = mock(RetryContext.class);
    when(retryContext.getLastThrowable()).thenReturn(messagingException);

    Message message = mock(Message.class);
    when(messagingException.getFailedMessage()).thenReturn(message);

    MessageHeaders messageHeaders = mock(MessageHeaders.class);
    when(messageHeaders.get("gcp_pubsub_original_message")).thenReturn(originalMessage);
    when(message.getHeaders()).thenReturn(messageHeaders);

    ProjectSubscriptionName projectSubscriptionName = mock(ProjectSubscriptionName.class);
    when(originalMessage.getProjectSubscriptionName()).thenReturn(projectSubscriptionName);
    when(projectSubscriptionName.getSubscription()).thenReturn("TEST SUBSCRIPTION");

    ByteString byteString = ByteString.copyFrom("TEST PAYLOAD".getBytes());
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(byteString).build();
    when(originalMessage.getPubsubMessage()).thenReturn(pubsubMessage);

    when(exceptionManagerClient.reportException(
            anyString(), anyString(), anyString(), any(Throwable.class), anyString()))
        .thenReturn(exceptionReportResponse);
    return retryContext;
  }
}
