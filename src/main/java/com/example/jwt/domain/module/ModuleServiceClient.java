package com.example.jwt.domain.module;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * REST client for the module_service. All calls are wrapped with a
 * Resilience4j Retry + CircuitBreaker + TimeLimiter — configured under the
 * name "module-service" in application.properties.
 */
@Component
public class ModuleServiceClient {

  private static final Logger log = LoggerFactory.getLogger(ModuleServiceClient.class);

  private final RestClient restClient;

  public ModuleServiceClient(RestClient moduleServiceRestClient) {
    this.restClient = moduleServiceRestClient;
  }

  /**
   * Returns true if the module exists in module_service, false if not.
   * Throws ModuleServiceUnavailableException when the breaker opens or
   * upstream 5xx / connectivity errors persist through all retries.
   */
  @CircuitBreaker(name = "module-service", fallbackMethod = "isAvailableFallback")
  @Retry(name = "module-service")
  public boolean isAvailable(UUID moduleId) {
    try {
      HttpStatusCode status = restClient.get()
          .uri("/api/v1/modules/{id}", moduleId)
          .retrieve()
          .toBodilessEntity()
          .getStatusCode();
      return status.is2xxSuccessful();
    } catch (HttpClientErrorException.NotFound notFound) {
      return false;
    }
  }

  @CircuitBreaker(name = "module-service", fallbackMethod = "assignFallback")
  @Retry(name = "module-service")
  public void assign(UUID userId, UUID moduleId) {
    try {
      restClient.put()
          .uri("/api/v1/users/{userId}/modules/{moduleId}", userId, moduleId)
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.NotFound notFound) {
      throw new ModuleNotFoundException(moduleId);
    }
  }

  // --- Fallbacks --------------------------------------------------------------

  @SuppressWarnings("unused")
  private boolean isAvailableFallback(UUID moduleId, Throwable throwable) {
    return handleUpstreamFailure("check availability", moduleId, throwable);
  }

  @SuppressWarnings("unused")
  private void assignFallback(UUID userId, UUID moduleId, Throwable throwable) {
    handleUpstreamFailure("assign", moduleId, throwable);
  }

  private boolean handleUpstreamFailure(String op, UUID moduleId, Throwable throwable) {
    if (throwable instanceof ModuleNotFoundException notFound) {
      throw notFound;
    }
    log.warn("module_service {} failed for module {}: {}", op, moduleId, throwable.getMessage());
    throw new ModuleServiceUnavailableException(
        "module_service is not reachable while trying to " + op, throwable);
  }

  /**
   * Bridge for TimeLimiter — reflection-only Resilience4j path that keeps
   * blocking callers unaware of async plumbing while still enforcing an
   * overall wall-clock budget.
   */
  @TimeLimiter(name = "module-service")
  @CircuitBreaker(name = "module-service")
  @Retry(name = "module-service")
  public CompletableFuture<Boolean> isAvailableAsync(UUID moduleId) {
    return CompletableFuture.supplyAsync(() -> isAvailable(moduleId));
  }

  static boolean isTransient(Throwable throwable) {
    if (throwable instanceof ResourceAccessException) {
      return true;
    }
    if (throwable instanceof HttpServerErrorException) {
      return true;
    }
    return throwable.getCause() != null && isTransient(throwable.getCause());
  }
}
