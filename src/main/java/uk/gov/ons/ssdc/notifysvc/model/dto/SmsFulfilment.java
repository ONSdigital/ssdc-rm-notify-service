package uk.gov.ons.ssdc.notifysvc.model.dto;

import lombok.Data;

import javax.persistence.Id;
import java.util.UUID;

@Data
public class SmsFulfilment {

    @Id
    private UUID caseId;

    private String telephoneNumber;

}
