package uk.gov.ons.ssdc.notifysvc.endpoint;

import static uk.gov.ons.ssdc.notifysvc.utils.PersonalisationTemplateHelper.buildPersonalisationFromTemplate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import uk.gov.ons.ssdc.notifysvc.config.NotifyServiceRefMapping;
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
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

@RestController
@RequestMapping(value = "/email-fulfilment")
public class EmailFulfilmentEndpoint {

  private final EmailRequestService emailRequestService;
  private final CaseRepository caseRepository;
  private final EmailTemplateRepository emailTemplateRepository;
  private final NotifyServiceRefMapping notifyServiceRefMapping;

  private static final Logger log = LoggerFactory.getLogger(EmailFulfilmentEndpoint.class);

  @Autowired
  public EmailFulfilmentEndpoint(
      EmailRequestService emailRequestService,
      CaseRepository caseRepository,
      EmailTemplateRepository emailTemplateRepository,
      NotifyServiceRefMapping notifyServiceRefMapping) {
    this.emailRequestService = emailRequestService;
    this.caseRepository = caseRepository;
    this.emailTemplateRepository = emailTemplateRepository;
    this.notifyServiceRefMapping = notifyServiceRefMapping;
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
          responseStatusException.getStatusCode());
    }

    Optional<UacQidCreatedPayloadDTO> newUacQidPair =
        emailRequestService.fetchNewUacQidPairIfRequired(emailTemplate.getTemplate());

    Map<String, String> emailPersonalisation =
        buildPersonalisationTemplateValues(
            emailTemplate,
            caze,
            newUacQidPair,
            request.getPayload().getEmailFulfilment().getPersonalisation());
    String notifyServiceRef = emailTemplate.getNotifyServiceRef();

    // NOTE: Here we are sending the enriched event BEFORE we make the call to send the email. This
    // is to be certain that the record of the UAC link is not lost. If we were to send the email
    // first then the event publish failed it would leave the requester with a broken UAC we would
    // be unable to fix
    emailRequestService.buildAndSendEmailConfirmation(
        request.getPayload().getEmailFulfilment().getCaseId(),
        request.getPayload().getEmailFulfilment().getPackCode(),
        request.getPayload().getEmailFulfilment().getUacMetadata(),
        request.getPayload().getEmailFulfilment().getPersonalisation(),
        newUacQidPair,
        false,
        request.getHeader().getSource(),
        request.getHeader().getChannel(),
        request.getHeader().getCorrelationId(),
        request.getHeader().getOriginatingUser());

    sendEmail(
        request.getPayload().getEmailFulfilment().getEmail(),
        emailTemplate,
        emailPersonalisation,
        request.getHeader().getCorrelationId().toString(),
        notifyServiceRef);

    return new ResponseEntity<>(createEmailSuccessResponse(newUacQidPair), HttpStatus.OK);
  }

  private Map<String, String> buildPersonalisationTemplateValues(
      EmailTemplate emailTemplate,
      Case caze,
      Optional<UacQidCreatedPayloadDTO> uacQidPair,
      Map<String, String> requestPersonalisation) {
    if (uacQidPair.isPresent()) {
      return buildPersonalisationFromTemplate(
          emailTemplate.getTemplate(),
          caze,
          uacQidPair.get().getUac(),
          uacQidPair.get().getQid(),
          requestPersonalisation);
    }
    return buildPersonalisationFromTemplate(
        emailTemplate.getTemplate(), caze, requestPersonalisation);
  }

  private EmailFulfilmentResponse createEmailSuccessResponse(
      Optional<UacQidCreatedPayloadDTO> newUacQidPair) {
    if (newUacQidPair.isPresent()) {
      String uacHash = HashHelper.hash(newUacQidPair.get().getUac());
      return new EmailFulfilmentResponseSuccess(uacHash, newUacQidPair.get().getQid());
    } else {
      return new EmailFulfilmentEmptyResponseSuccess();
    }
  }

  public void validateRequestAndFetchEmailTemplate(
      RequestDTO emailFulfilmentRequest, Case caze, EmailTemplate emailTemplate) {
    validateRequestHeader(emailFulfilmentRequest.getHeader());
    EmailFulfilment emailFulfilment = emailFulfilmentRequest.getPayload().getEmailFulfilment();
    validateTemplateOnSurvey(emailTemplate, caze.getCollectionExercise().getSurvey());
    validateEmailAddress(emailFulfilment.getEmail());
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

  private void validateEmailAddress(String emailAddress) {
    Optional<String> validationFailure = emailRequestService.validateEmailAddress(emailAddress);
    if (validationFailure.isPresent()) {
      String responseMessage = String.format("Invalid email address: %s", validationFailure.get());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, responseMessage);
    }
  }

  private void sendEmail(
      String emailAddress,
      EmailTemplate emailTemplate,
      Map<String, String> emailTemplatePersonalization,
      String reference,
      String notifyServiceRef) {

    NotificationClient notificationClient =
        notifyServiceRefMapping.getNotifyClient(notifyServiceRef);

    try {
      notificationClient.sendEmail(
          emailTemplate.getNotifyTemplateId().toString(),
          emailAddress,
          emailTemplatePersonalization,
          reference);
    } catch (NotificationClientException e) {
      log.atError()
          .setMessage("Error with Gov Notify when attempting to send email")
          .setCause(e)
          .log();
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Error with Gov Notify when attempting to send email",
          e);
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
