package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.UUID;
import javax.persistence.*;
import lombok.Data;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true) // Bidirectional relationship causes IDE stackoverflow
@Entity
@Data
public class UserGroupPermission {
  @Id private UUID id;

  @ManyToOne private UserGroup group;

  @ManyToOne private Survey survey;

  @Enumerated(EnumType.STRING)
  @Column
  private UserGroupAuthorisedActivityType authorisedActivity;
}
