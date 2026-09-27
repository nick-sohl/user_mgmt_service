package com.example.jwt.domain.module;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "module-service")
public class ModuleServiceProperties {

  private String baseUrl = "http://module-service-prod.module-service-prod.svc.cluster.local:8080";
  private Duration connectTimeout = Duration.ofMillis(500);
  private Duration readTimeout = Duration.ofSeconds(2);

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }
}
