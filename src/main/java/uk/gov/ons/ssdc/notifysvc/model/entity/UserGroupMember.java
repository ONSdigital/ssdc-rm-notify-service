package uk.gov.ons.ssdc.notifysvc.model.entity;

import lombok.Data;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.UUID;

@ToString(onlyExplicitlyIncluded = true) // Bidirectional relationship causes IDE stackoverflow
@Entity
@Data
public class UserGroupMember {
  @Id private UUID id;

  @ManyToOne private User user;

  @ManyToOne private UserGroup group;
}
