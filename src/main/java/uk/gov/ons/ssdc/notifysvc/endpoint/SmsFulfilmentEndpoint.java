package uk.gov.ons.ssdc.notifysvc.endpoint;

import static uk.gov.ons.ssdc.notifysvc.utils.PersonalisationTemplateHelper.buildPersonalisationFromTemplate;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.RequestHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SmsFulfilmentEmptyResponseSuccess;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SmsFulfilmentResponse;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SmsFulfilmentResponseError;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.SmsFulfilmentResponseSuccess;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;
import uk.gov.ons.ssdc.notifysvc.service.SmsRequestService;
import uk.gov.ons.ssdc.notifysvc.utils.HashHelper;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

@RestController
@RequestMapping(value = "/sms-fulfilment")
public class SmsFulfilmentEndpoint {
  private static final Logger logger = LoggerFactory.getLogger(SmsFulfilmentEndpoint.class);

  private final SmsRequestService smsRequestService;
  private final CaseRepository caseRepository;
  private final SmsTemplateRepository smsTemplateRepository;

  private final Map<String, Map<String, Object>> notificationClientApi;

  @Autowired
  public SmsFulfilmentEndpoint(
      SmsRequestService smsRequestService,
      CaseRepository caseRepository,
      SmsTemplateRepository smsTemplateRepository,
      Map<String, Map<String, Object>> notificationClientApi) {
    this.smsRequestService = smsRequestService;
    this.caseRepository = caseRepository;
    this.smsTemplateRepository = smsTemplateRepository;
    this.notificationClientApi = notificationClientApi;
  }

  @Operation(description = "SMS Fulfilment Request")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description =
                "Send an SMS fulfilment for a case. Returns uacHash & QID if template has UAC/QID, or empty response if not",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = SmsFulfilmentResponseSuccess.class))
            }),
        @ApiResponse(
            responseCode = "400",
            description = "SMS Fulfilment request failed validation",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = SmsFulfilmentResponseError.class))
            }),
        @ApiResponse(
            responseCode = "500",
            description = "Error with Gov Notify when attempting to send SMS",
            content = @Content)
      })
  @PostMapping
  public ResponseEntity<SmsFulfilmentResponse> smsFulfilment(@RequestBody RequestDTO request) {

    Case caze;
    SmsTemplate smsTemplate;
    try {
      caze = findCaseById(request.getPayload().getSmsFulfilment().getCaseId());
      smsTemplate =
          findSmsTemplateByPackCode(request.getPayload().getSmsFulfilment().getPackCode());
      validateRequestAndFetchSmsTemplate(request, caze, smsTemplate);
    } catch (ResponseStatusException responseStatusException) {
      return new ResponseEntity<>(
          new SmsFulfilmentResponseError(responseStatusException.getReason()),
          responseStatusException.getStatus());
    }

    Optional<UacQidCreatedPayloadDTO> newUacQidPair =
        smsRequestService.fetchNewUacQidPairIfRequired(smsTemplate.getTemplate());

    Map<String, String> smsPersonalisation =
        buildPersonalisationTemplateValues(
            smsTemplate,
            caze,
            newUacQidPair,
            request.getPayload().getSmsFulfilment().getPersonalisation());

    // NOTE: Here we are sending the enriched event BEFORE we make the call to send the SMS.
    // This is to be certain that the record of the UAC link is not lost. If we were to send the SMS
    // first then the event publish failed it would leave the requester with a broken UAC we would
    // be unable to fix
    smsRequestService.buildAndSendSmsConfirmation(
        request.getPayload().getSmsFulfilment().getCaseId(),
        request.getPayload().getSmsFulfilment().getPackCode(),
        request.getPayload().getSmsFulfilment().getUacMetadata(),
        request.getPayload().getSmsFulfilment().getPersonalisation(),
        newUacQidPair,
        false,
        request.getHeader().getSource(),
        request.getHeader().getChannel(),
        request.getHeader().getCorrelationId(),
        request.getHeader().getOriginatingUser());

    String notifyServiceRef = smsTemplate.getNotifyServiceRef();
    Map<String, Object> service = notificationClientApi.get(notifyServiceRef);
    String senderId = (String) service.get("sender-id");
    NotificationClient notificationClient = (NotificationClient) service.get("client");

    sendSms(
        request.getPayload().getSmsFulfilment().getPhoneNumber(),
        smsTemplate,
        smsPersonalisation,
        senderId,
        notificationClient);

    return new ResponseEntity<>(createSmsSuccessResponse(newUacQidPair), HttpStatus.OK);
  }

  private Map<String, String> buildPersonalisationTemplateValues(
      SmsTemplate smsTemplate,
      Case caze,
      Optional<UacQidCreatedPayloadDTO> uacQidPair,
      Map<String, String> requestPersonalisation) {
    if (uacQidPair.isPresent()) {
      return buildPersonalisationFromTemplate(
          smsTemplate.getTemplate(),
          caze,
          uacQidPair.get().getUac(),
          uacQidPair.get().getQid(),
          requestPersonalisation);
    }
    return buildPersonalisationFromTemplate(
        smsTemplate.getTemplate(), caze, requestPersonalisation);
  }

  private SmsFulfilmentResponse createSmsSuccessResponse(
      Optional<UacQidCreatedPayloadDTO> newUacQidPair) {
    if (newUacQidPair.isPresent()) {
      String uacHash = HashHelper.hash(newUacQidPair.get().getUac());
      return new SmsFulfilmentResponseSuccess(uacHash, newUacQidPair.get().getQid());
    } else {
      return new SmsFulfilmentEmptyResponseSuccess();
    }
  }

  public void validateRequestAndFetchSmsTemplate(
      RequestDTO smsFulfilmentRequest, Case caze, SmsTemplate smsTemplate) {
    validateRequestHeader(smsFulfilmentRequest.getHeader());
    SmsFulfilment smsFulfilment = smsFulfilmentRequest.getPayload().getSmsFulfilment();
    validateTemplateOnSurvey(smsTemplate, caze.getCollectionExercise().getSurvey());
    validatePhoneNumber(smsFulfilment.getPhoneNumber());
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

  private void validatePhoneNumber(String phoneNumber) {
    if (!smsRequestService.validatePhoneNumber(phoneNumber)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid phone number");
    }
  }

  private void sendSms(
      String phoneNumber,
      SmsTemplate smsTemplate,
      Map<String, String> smsTemplateValues,
      String senderId,
      NotificationClient notificationClient) {
    try {
      notificationClient.sendSms(
          smsTemplate.getNotifyTemplateId().toString(), phoneNumber, smsTemplateValues, senderId);
    } catch (NotificationClientException e) {
      logger.error("Error with Gov Notify when attempting to send SMS", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error with Gov Notify when attempting to send SMS", e);
    }
  }

  private void validateTemplateOnSurvey(SmsTemplate template, Survey survey) {
    if (!smsRequestService.isSmsTemplateAllowedOnSurvey(template, survey)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "The template for this pack code is not allowed on this survey");
    }
  }

  public SmsTemplate findSmsTemplateByPackCode(String packCode) {
    return smsTemplateRepository
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
