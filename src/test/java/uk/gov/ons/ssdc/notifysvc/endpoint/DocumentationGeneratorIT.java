package uk.gov.ons.ssdc.notifysvc.endpoint;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class DocumentationGeneratorIT {
  @LocalServerPort private int port;

  /*
  Clearly this is NOT a test. This is a way of using Spring Boot's integration testing plugin utils
  to update our machine-generated documentation without having to include any of it in the
  production build.

  The alternative would have been to use springdoc-openapi-maven-plugin but this won't work because
  our app won't start up without a database etc, and the app has to be running for the plugin to
  work. Also, it would have meant bundling all the openAPI JARs into our prod build and exposing
  endpoints which could be a security risk.
   */
  @Test
  public void generateDocs() throws IOException, InterruptedException {
    RestTemplate restTemplate = new RestTemplate();
    String url = "http://localhost:" + port + "/v3/api-docs/";
    String apiSpec = restTemplate.getForObject(url, String.class);
    assertThat(apiSpec).isNotBlank();

    try (FileOutputStream fos = new FileOutputStream("docs/api.json")) {
      fos.write(apiSpec.getBytes());
    }

    Process process = Runtime.getRuntime().exec("npx widdershins docs/api.json -o docs/api.md");
    process.waitFor();
    int exitStatus = process.exitValue();

    // If this is failing, have you run `sudo npm install -g widdershins` like it says in README.md?
    assertThat(exitStatus).isZero();
  }
}
