package uk.gov.ons.ssdc.notifysvc.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ssdc.notifysvc.model.dto.api.UacQidCreatedPayloadDTO;

@Component
public class UacQidServiceClient {

  @Value("${uacservice.connection.scheme}")
  private String scheme;

  @Value("${uacservice.connection.host}")
  private String host;

  @Value("${uacservice.connection.port}")
  private String port;

  public UacQidCreatedPayloadDTO generateUacQid() {

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents();
    ResponseEntity<UacQidCreatedPayloadDTO> responseEntity =
        restTemplate.exchange(
            uriComponents.toUri(), HttpMethod.GET, null, UacQidCreatedPayloadDTO.class);
    return responseEntity.getBody();
  }

  private UriComponents createUriComponents() {
    return UriComponentsBuilder.newInstance().scheme(scheme).host(host).port(port).build().encode();
  }
}
