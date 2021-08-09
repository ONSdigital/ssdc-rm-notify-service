package uk.gov.ons.ssdc.notifysvc.endpoint;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ssdc.notifysvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.ssdc.notifysvc.model.dto.SmsFulfilment;
import uk.gov.ons.ssdc.notifysvc.model.repository.CaseRepository;

import java.util.UUID;

@RestController
@RequestMapping(value = "/smsfulfilment")
public class SmsFulfilmentEndpoint {

    private final SmsFulfilmentEndpoint smsFulfilmentEndpoint;
    private final CaseRepository caseRepository;


    public SmsFulfilmentEndpoint(SmsFulfilmentEndpoint smsFulfilmentEndpoint, CaseRepository caseRepository) {
        this.smsFulfilmentEndpoint = smsFulfilmentEndpoint;
        this.caseRepository = caseRepository;
    }

    @PostMapping
    public void smsFulfilment(@RequestBody ResponseManagementEvent responseManagementEvent) {

        SmsFulfilment smsFulfilment = responseManagementEvent.getPayload().getSmsFulfilment();

        if (!caseRepository.existsById(smsFulfilment.getCaseId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("CaseId %s does not exist", smsFulfilment.getCaseId()));
        }










    }
}
