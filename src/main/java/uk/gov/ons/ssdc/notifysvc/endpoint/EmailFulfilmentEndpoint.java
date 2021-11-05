package uk.gov.ons.ssdc.notifysvc.endpoint;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.EmailFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.EmailFulfilmentEmptyResponseSuccess;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.EmailFulfilmentResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.EmailFulfilmentResponseError;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.EmailFulfilmentResponseSuccess;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.EmailTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.EmailRequestService;
import uk.gov.ons.ssdc.notifysvc.utils.HashHelper;
import uk.gov.service.notify.NotificationClientApi;
import uk.gov.service.notify.NotificationClientException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/email-fulfilment")
public class EmailFulfilmentEndpoint {

  @Value("${notify.senderId}")
  private String senderId;

  private final EmailRequestService emailRequestService;
  private final CaseRepository caseRepository;
  private final EmailTemplateRepository emailTemplateRepository;
  private final NotificationClientApi notificationClientApi;

  private static final Logger logger = LoggerFactory.getLogger(EmailFulfilmentEndpoint.class);

  @Autowired
  public EmailFulfilmentEndpoint(
      EmailRequestService emailRequestService,
      CaseRepository caseRepository,
      EmailTemplateRepository emailTemplateRepository,
      NotificationClientApi notificationClientApi) {
    this.emailRequestService = emailRequestService;
    this.caseRepository = caseRepository;
    this.emailTemplateRepository = emailTemplateRepository;
    this.notificationClientApi = notificationClientApi;
  }

  @Operation(description = "Email Fulfilment Request")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description =
                "Send an email fulfilment for a case. Returns uacHash & QID if template has UAC/QID, or empty response if not",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = EmailFulfilmentResponseSuccess.class))
            }),
        @ApiResponse(
            responseCode = "400",
            description = "Email Fulfilment request failed validation",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = EmailFulfilmentResponseError.class))
            }),
        @ApiResponse(
            responseCode = "500",
            description = "Error with Gov Notify when attempting to send email",
            content = @Content)
      })
  @PostMapping
  public ResponseEntity<EmailFulfilmentResponse> emailFulfilment(@RequestBody RequestDTO request) {

    Case caze;
    EmailTemplate emailTemplate;
    try {
      caze = findCaseById(request.getPayload().getEmailFulfilment().getCaseId());
      emailTemplate =
          findEmailTemplateByPackCode(request.getPayload().getEmailFulfilment().getPackCode());
      validateRequestAndFetchEmailTemplate(request, caze, emailTemplate);
    } catch (ResponseStatusException responseStatusException) {
      return new ResponseEntity<>(
          new EmailFulfilmentResponseError(responseStatusException.getReason()),
          responseStatusException.getStatus());
    }

    UacQidCreatedPayloadDTO newUacQidPair =
        emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate());

    Map<String, String> emailTemplateValues =
        buildPersonalisationTemplateValues(emailTemplate, caze, newUacQidPair);

    // NOTE: Here we are sending the enriched event BEFORE we make the call to send the email.
    // This is to be certain that the record of the UAC link is not lost. If we were to send the
    // email
    // first then the event publish failed it would leave the requester with a broken UAC we would
    // be unable to fix
    emailRequestService.buildAndSendEnrichedEmailFulfilment(
        request.getPayload().getEmailFulfilment().getCaseId(),
        request.getPayload().getEmailFulfilment().getPackCode(),
        request.getPayload().getEmailFulfilment().getUacMetadata(),
        newUacQidPair,
        request.getHeader().getSource(),
        request.getHeader().getChannel(),
        request.getHeader().getCorrelationId(),
        request.getHeader().getOriginatingUser());

    sendEmail(
        request.getPayload().getEmailFulfilment().getEmail(), emailTemplate, emailTemplateValues);

    return new ResponseEntity<>(createEmailSuccessResponse(newUacQidPair), HttpStatus.OK);
  }

  private Map<String, String> buildPersonalisationTemplateValues(
      EmailTemplate emailTemplate, Case caze, UacQidCreatedPayloadDTO uacQidPair) {
    if (uacQidPair != null) {
      return emailRequestService.buildPersonalisationFromTemplate(
          emailTemplate, caze, uacQidPair.getUac(), uacQidPair.getQid());
    }
    return emailRequestService.buildPersonalisationFromTemplate(emailTemplate, caze, null, null);
  }

  private EmailFulfilmentResponse createEmailSuccessResponse(UacQidCreatedPayloadDTO newUacQidPair) {
    if (newUacQidPair != null) {
      String uacHash = HashHelper.hash(newUacQidPair.getUac());
      return new EmailFulfilmentResponseSuccess(uacHash, newUacQidPair.getQid());
    } else {
      return new EmailFulfilmentEmptyResponseSuccess();
    }
  }

  public void validateRequestAndFetchEmailTemplate(
      RequestDTO emailFulfilmentRequest, Case caze, EmailTemplate emailTemplate) {
    validateRequestHeader(emailFulfilmentRequest.getHeader());
    EmailFulfilment emailFulfilment = emailFulfilmentRequest.getPayload().getEmailFulfilment();
    validateTemplateOnSurvey(emailTemplate, caze.getCollectionExercise().getSurvey());
    validateEmailAdress(emailFulfilment.getEmail());
  }

  private void validateRequestHeader(RequestHeaderDTO requestHeader) {
    if (requestHeader.getCorrelationId() == null
        || StringUtils.isBlank(requestHeader.getChannel())
        || StringUtils.isBlank(requestHeader.getSource())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid request header: correlationId, channel and source are mandatory");
    }
  }

  private void validateEmailAdress(String emailAddress) {
    if (!emailRequestService.validateEmailAddress(emailAddress)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email address");
    }
  }

  private void sendEmail(
      String emailAddress, EmailTemplate emailTemplate, Map<String, String> emailTemplateValues) {
    try {
      notificationClientApi.sendEmail(
          emailTemplate.getNotifyTemplateId().toString(), emailAddress, emailTemplateValues, senderId);
    } catch (NotificationClientException e) {
      logger.error("Error with Gov Notify when attempting to send email", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error with Gov Notify when attempting to send email", e);
    }
  }

  private void validateTemplateOnSurvey(EmailTemplate emailTemplate, Survey survey) {
    if (!emailRequestService.isEmailTemplateAllowedOnSurvey(emailTemplate, survey)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The template for this pack code is not allowed on this survey");
    }
  }

  public EmailTemplate findEmailTemplateByPackCode(String packCode) {
    return emailTemplateRepository
        .findById(packCode)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A template does not exist with this pack code"));
  }

  public Case findCaseById(UUID caseId) {
    return caseRepository
        .findById(caseId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The case does not exist"));
  }
}
