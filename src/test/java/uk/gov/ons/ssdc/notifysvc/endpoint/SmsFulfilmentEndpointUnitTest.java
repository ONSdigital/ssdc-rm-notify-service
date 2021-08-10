package uk.gov.ons.ssdc.notifysvc.endpoint;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.ons.ssdc.notifysvc.model.repository.SmsTemplateRepository;

@ExtendWith(MockitoExtension.class)
class SmsFulfilmentEndpointUnitTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Mock private SmsTemplateRepository smsTemplateRepository;

  @InjectMocks private SmsFulfilmentEndpoint smsFulfilmentEndpoint;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(smsFulfilmentEndpoint).build();
  }

  @Test
  void smsFulfilment() {}
}
