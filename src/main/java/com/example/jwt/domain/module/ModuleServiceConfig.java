package com.example.jwt.domain.module;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ModuleServiceProperties.class)
public class ModuleServiceConfig {

  @Bean
  public RestClient moduleServiceRestClient(ModuleServiceProperties props,
      RestClient.Builder builder) {
    ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder
        .detect()
        .build(ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(props.getConnectTimeout())
            .withReadTimeout(props.getReadTimeout()));
    return builder
        .baseUrl(props.getBaseUrl())
        .requestFactory(factory)
        .build();
  }
}
