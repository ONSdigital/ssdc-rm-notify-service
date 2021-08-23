package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.UUID;
import javax.persistence.*;
import lombok.Data;

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
