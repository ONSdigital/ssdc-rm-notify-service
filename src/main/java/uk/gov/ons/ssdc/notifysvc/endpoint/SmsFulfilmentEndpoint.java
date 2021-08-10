package uk.gov.ons.ssdc.notifysvc.endpoint;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.notifysvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.entity.Case;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;

@RestController
@RequestMapping(value = "/smsfulfilment")
public class SmsFulfilmentEndpoint {

  private final SmsFulfilmentEndpoint smsFulfilmentEndpoint;
  private final CaseRepository caseRepository;

  public SmsFulfilmentEndpoint(
      SmsFulfilmentEndpoint smsFulfilmentEndpoint, CaseRepository caseRepository) {
    this.smsFulfilmentEndpoint = smsFulfilmentEndpoint;
    this.caseRepository = caseRepository;
  }

  @PostMapping
  public void smsFulfilment(@RequestBody ResponseManagementEvent responseManagementEvent) {

    SmsFulfilment smsFulfilment = responseManagementEvent.getPayload().getSmsFulfilment();

    Case caze =
            caseRepository
                    .findById(smsFulfilment.getCaseId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    caze.setId(smsFulfilment.getCaseId());
    caze.setSample(smsFulfilment.getTelephoneNumber());
  }
}
