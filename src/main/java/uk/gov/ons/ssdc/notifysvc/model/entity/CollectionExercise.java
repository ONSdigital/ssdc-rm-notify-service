package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.List;
import java.util.UUID;
import javax.persistence.*;
import lombok.Data;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true) // Bidirectional relationship causes IDE stackoverflow
@Data
@Entity
public class CollectionExercise {
  @Id private UUID id;

  @Column private String name;

  @ManyToOne private Survey survey;

  @OneToMany(mappedBy = "collectionExercise")
  private List<Case> cases;

  @OneToMany(mappedBy = "collectionExercise")
  private List<ActionRule> actionRules;

  @OneToMany(mappedBy = "collectionExercise")
  private List<Job> jobs;
}
