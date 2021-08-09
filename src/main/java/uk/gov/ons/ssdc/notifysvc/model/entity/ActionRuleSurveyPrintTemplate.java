package uk.gov.ons.ssdc.notifysvc.model.entity;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.UUID;

@Data
@Entity
public class ActionRuleSurveyPrintTemplate {
  @Id private UUID id;

  @ManyToOne private Survey survey;

  @ManyToOne private PrintTemplate printTemplate;
}
