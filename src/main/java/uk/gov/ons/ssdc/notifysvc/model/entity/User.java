package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.OneToMany;
import javax.persistence.Table;
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

  @Column(nullable = false)
  private String email;

  @OneToMany(mappedBy = "user")
  private List<UserGroupMember> memberOf;

  @OneToMany(mappedBy = "user")
  private List<UserGroupAdmin> adminOf;
}
