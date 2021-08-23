package uk.gov.ons.ssdc.notifysvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

@ToString(onlyExplicitlyIncluded = true) // Bidirectional relationship causes IDE stackoverflow
@Data
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
@Entity
public class Survey {
  @Id private UUID id;

  @Column private String name;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private String sampleValidationRules;

  @Column private boolean sampleWithHeaderRow;

  @Column private char sampleSeparator;

  @OneToMany(mappedBy = "survey")
  private List<CollectionExercise> collectionExercises;

  @OneToMany(mappedBy = "survey")
  private List<ActionRuleSurveyPrintTemplate> actionRulePrintTemplates;

  @OneToMany(mappedBy = "survey")
  private List<FulfilmentSurveyPrintTemplate> fulfilmentPrintTemplates;
}
