package uk.gov.ons.ssdc.notifysvc.messaging;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.google.protobuf.ByteString;
import net.logstash.logback.encoder.org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.stereotype.Component;
import uk.gov.ons.ssdc.notifysvc.client.ExceptionManagerClient;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.ExceptionReportResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SkippedMessage;
import uk.gov.ons.ssdc.notifysvc.utils.HashHelper;

@Component
public class ManagedMessageRecoverer implements RecoveryCallback<Object> {
  private static final Logger log = LoggerFactory.getLogger(ManagedMessageRecoverer.class);
  private static final String SERVICE_NAME = "Notify Service";

  @Value("${messagelogging.logstacktraces}")
  private boolean logStackTraces;

  private final ExceptionManagerClient exceptionManagerClient;

  public ManagedMessageRecoverer(ExceptionManagerClient exceptionManagerClient) {
    this.exceptionManagerClient = exceptionManagerClient;
  }

  @Override
  public Object recover(RetryContext retryContext) throws Exception {
    if (!(retryContext.getLastThrowable() instanceof MessagingException)) {
      log.error(
          "Super duper unexpected kind of error, so going to fail very noisily",
          retryContext.getLastThrowable());
      throw new RuntimeException(retryContext.getLastThrowable());
    }

    MessagingException messagingException = (MessagingException) retryContext.getLastThrowable();
    Message<?> message = messagingException.getFailedMessage();
    BasicAcknowledgeablePubsubMessage originalMessage =
        (BasicAcknowledgeablePubsubMessage)
            message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE);
    String subscriptionName = originalMessage.getProjectSubscriptionName().getSubscription();
    ByteString originalMessageByteString = originalMessage.getPubsubMessage().getData();
    byte[] rawMessageBody = new byte[originalMessageByteString.size()];
    originalMessageByteString.copyTo(rawMessageBody, 0);

    String messageHash = HashHelper.hash(rawMessageBody);

    String stackTraceRootCause = findUsefulRootCauseInStackTrace(retryContext.getLastThrowable());

    Throwable cause = retryContext.getLastThrowable();
    if (retryContext.getLastThrowable() != null
        && retryContext.getLastThrowable().getCause() != null
        && retryContext.getLastThrowable().getCause().getCause() != null) {
      cause = retryContext.getLastThrowable().getCause().getCause();
    }

    ExceptionReportResponse reportResult =
        getExceptionReportResponse(cause, messageHash, stackTraceRootCause, subscriptionName);

    if (skipMessage(
        reportResult,
        messageHash,
        rawMessageBody,
        retryContext.getLastThrowable(),
        originalMessage,
        subscriptionName)) {
      return null; // Our work here is done
    }

    peekMessage(reportResult, messageHash, rawMessageBody);

    logMessage(
        reportResult, retryContext.getLastThrowable().getCause(), messageHash, stackTraceRootCause);

    // Reject the original message where it'll be retried at some future point in time
    originalMessage.nack();

    return null;
  }

  private ExceptionReportResponse getExceptionReportResponse(
      Throwable cause, String messageHash, String stackTraceRootCause, String subscriptionName) {
    ExceptionReportResponse reportResult = null;
    try {
      reportResult =
          exceptionManagerClient.reportException(
              messageHash, SERVICE_NAME, subscriptionName, cause, stackTraceRootCause);
    } catch (Exception exceptionManagerClientException) {
      log.with("reason", exceptionManagerClientException.getMessage())
          .warn(
              "Could not report to Exception Manager. There will be excessive logging until resolved");
    }
    return reportResult;
  }

  private boolean skipMessage(
      ExceptionReportResponse reportResult,
      String messageHash,
      byte[] rawMessageBody,
      Throwable cause,
      BasicAcknowledgeablePubsubMessage originalMessage,
      String subscriptionName) {

    if (reportResult == null || !reportResult.isSkipIt()) {
      return false;
    }

    boolean result = false;

    // Make certain that we have a copy of the message before quarantining it
    try {
      SkippedMessage skippedMessage = new SkippedMessage();
      skippedMessage.setMessageHash(messageHash);
      skippedMessage.setMessagePayload(rawMessageBody);
      skippedMessage.setService(SERVICE_NAME);
      skippedMessage.setSubscription(subscriptionName);
      skippedMessage.setContentType("application/json");
      skippedMessage.setHeaders(null);
      skippedMessage.setRoutingKey(null);
      exceptionManagerClient.storeMessageBeforeSkipping(skippedMessage);
      result = true;
    } catch (Exception exceptionManagerClientException) {
      log.with("message_hash", messageHash)
          .warn(
              "Unable to store a copy of the message. Will NOT be quarantining",
              exceptionManagerClientException);
    }

    // If the quarantined message is persisted OK then we can ACK the message
    if (result) {
      log.with("message_hash", messageHash).warn("Quarantined message");
    }

    return result;
  }

  private void peekMessage(
      ExceptionReportResponse reportResult, String messageHash, byte[] rawMessageBody) {
    if (reportResult == null || !reportResult.isPeek()) {
      return;
    }

    try {
      // Send it back to the exception manager so it can be peeked
      exceptionManagerClient.respondToPeek(messageHash, rawMessageBody);
    } catch (Exception respondException) {
      // Nothing we can do about this - ignore it
    }
  }

  private void logMessage(
      ExceptionReportResponse reportResult,
      Throwable cause,
      String messageHash,
      String stackTraceRootCause) {
    if (reportResult != null && !reportResult.isLogIt()) {
      return;
    }

    if (logStackTraces) {
      log.with("message_hash", messageHash).error("Could not process message", cause);
    } else {
      log.with("message_hash", messageHash)
          .with("cause", cause.getMessage())
          .with("root_cause", stackTraceRootCause)
          .error("Could not process message");
    }
  }

  private String findUsefulRootCauseInStackTrace(Throwable cause) {
    String[] stackTrace = ExceptionUtils.getRootCauseStackTrace(cause);

    // Iterate through the stack trace until we hit the first problem with our code
    for (String stackTraceLine : stackTrace) {
      if (stackTraceLine.contains("uk.gov.ons")) {
        return stackTraceLine;
      }
    }

    return stackTrace[0];
  }
}
