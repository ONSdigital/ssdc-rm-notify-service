package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.UUID;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import lombok.Data;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true) // Bidirectional relationship causes IDE stackoverflow
@Entity
@Data
public class UserGroupMember {
  @Id private UUID id;

  @ManyToOne(optional = false)
  private User user;

  @ManyToOne(optional = false)
  private UserGroup group;
}
