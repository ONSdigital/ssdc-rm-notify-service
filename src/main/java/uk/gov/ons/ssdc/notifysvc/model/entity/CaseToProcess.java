package uk.gov.ons.ssdc.notifysvc.model.entity;

import lombok.Data;

import javax.persistence.*;
import java.util.UUID;

@Data
@Entity
public class CaseToProcess {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(columnDefinition = "serial")
  private long id;

  @ManyToOne private Case caze;

  @ManyToOne private ActionRule actionRule;

  @Column private UUID batchId;

  @Column(nullable = false)
  private int batchQuantity;
}
