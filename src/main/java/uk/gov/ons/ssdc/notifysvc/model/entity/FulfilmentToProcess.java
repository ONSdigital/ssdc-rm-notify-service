package uk.gov.ons.ssdc.notifysvc.model.entity;

import lombok.Data;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Data
public class FulfilmentToProcess {

  @Id
  @Column(columnDefinition = "serial")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @ManyToOne private PrintTemplate printTemplate;

  @ManyToOne private Case caze;

  @Column private Integer batchQuantity;

  @Column private UUID batchId;
}
