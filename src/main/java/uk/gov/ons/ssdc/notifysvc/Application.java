package uk.gov.ons.ssdc.notifysvc;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import uk.gov.ons.ssdc.notifysvc.config.NotifyConfiguration;

@SpringBootApplication
@EnableConfigurationProperties
@EntityScan("uk.gov.ons.ssdc.common.model.entity")
@OpenAPIDefinition(
    info =
        @Info(
            title = "Notify Service",
            description = "Service for contacting respondents via Gov Notify SMS messages",
            version = "v1"),
    servers = {@Server(url = "http://localhost:8162")})
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
