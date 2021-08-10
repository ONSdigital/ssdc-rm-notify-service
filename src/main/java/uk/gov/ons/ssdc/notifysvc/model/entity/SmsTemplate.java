package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.Data;

@Data
@Entity
public class SmsTemplate {
  @Id private String packCode;

  @Column private UUID templateId;
}
