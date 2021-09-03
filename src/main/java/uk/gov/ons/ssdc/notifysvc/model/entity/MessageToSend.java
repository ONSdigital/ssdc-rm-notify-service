package uk.gov.ons.ssdc.notifysvc.model.entity;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import lombok.Data;
import org.hibernate.annotations.Type;

@Data
@Entity
public class MessageToSend {
  @Id private UUID id;

  @Column(nullable = false)
  private String destinationTopic;

  @Lob
  @Type(type = "org.hibernate.type.BinaryType")
  @Column(nullable = false)
  private byte[] messageBody;

  public void setMessageBody(String messageBodyStr) {
    if (messageBodyStr == null) {
      messageBody = null;
    } else {
      messageBody = messageBodyStr.getBytes();
    }
  }

  public String getMessageBody() {
    if (messageBody == null) {
      return null;
    }

    return new String(messageBody);
  }
}
