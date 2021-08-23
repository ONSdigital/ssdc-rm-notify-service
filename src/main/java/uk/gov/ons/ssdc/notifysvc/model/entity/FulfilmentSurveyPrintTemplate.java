package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.UUID;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import lombok.Data;

@Data
@Entity
public class FulfilmentSurveyPrintTemplate {
  @Id private UUID id;

  @ManyToOne private Survey survey;

  @ManyToOne private PrintTemplate printTemplate;
}
