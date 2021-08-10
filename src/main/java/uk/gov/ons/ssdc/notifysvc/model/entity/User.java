package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.List;
import java.util.UUID;
import javax.persistence.*;
import lombok.Data;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true) // Bidirectional relationship causes IDE stackoverflow
@Entity
@Data
@Table(
    name = "users",
    indexes = {@Index(name = "users_email_idx", columnList = "email", unique = true)})
public class User {
  @Id private UUID id;

  @Column private String email;

  @OneToMany(mappedBy = "user")
  private List<UserGroupMember> memberOf;

  @OneToMany(mappedBy = "user")
  private List<UserGroupAdmin> adminOf;
}
