package uk.gov.ons.ssdc.notifysvc.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.ExceptionReport;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.ExceptionReportResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.Peek;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SkippedMessage;

@Component
public class ExceptionManagerClient {

  @Value("${exceptionmanager.connection.scheme}")
  private String scheme;

  @Value("${exceptionmanager.connection.host}")
  private String host;

  @Value("${exceptionmanager.connection.port}")
  private String port;

  public ExceptionReportResponse reportException(
      String messageHash,
      String service,
      String subscription,
      Throwable cause,
      String stackTraceRootCause) {

    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setExceptionClass(cause.getClass().getName());
    exceptionReport.setExceptionMessage(cause.getMessage());
    exceptionReport.setExceptionRootCause(stackTraceRootCause);
    exceptionReport.setMessageHash(messageHash);
    exceptionReport.setService(service);
    exceptionReport.setSubscription(subscription);

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents("/reportexception");

    return restTemplate.postForObject(
        uriComponents.toUri(), exceptionReport, ExceptionReportResponse.class);
  }

  public void respondToPeek(String messageHash, byte[] payload) {

    Peek peekReply = new Peek();
    peekReply.setMessageHash(messageHash);
    peekReply.setMessagePayload(payload);

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents("/peekreply");

    restTemplate.postForObject(uriComponents.toUri(), peekReply, Void.class);
  }

  public void storeMessageBeforeSkipping(SkippedMessage skippedMessage) {

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents("/storeskippedmessage");

    restTemplate.postForObject(uriComponents.toUri(), skippedMessage, Void.class);
  }

  private UriComponents createUriComponents(String path) {
    return UriComponentsBuilder.newInstance()
        .scheme(scheme)
        .host(host)
        .port(port)
        .path(path)
        .build()
        .encode();
  }
}
