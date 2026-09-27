package com.example.jwt.domain.module;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ModuleServiceProperties.class)
public class ModuleServiceConfig {

  @Bean
  public RestClient moduleServiceRestClient(ModuleServiceProperties props) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(toIntMillis(props.getConnectTimeout()));
    factory.setReadTimeout(toIntMillis(props.getReadTimeout()));
    return RestClient.builder()
        .baseUrl(props.getBaseUrl())
        .requestFactory(factory)
        .build();
  }

  private static int toIntMillis(Duration duration) {
    long millis = duration.toMillis();
    return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
  }
}
