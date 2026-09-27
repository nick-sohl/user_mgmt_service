package com.example.jwt.domain.module;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aufgabe 6 — the user_mgmt_service surface for module assignment. Delegates
 * availability + assignment to module_service, wrapped in the resilience
 * primitives configured on {@link ModuleServiceClient}.
 */
@RestController
@RequestMapping("/users")
public class UserModuleController {

  private static final Logger log = LoggerFactory.getLogger(UserModuleController.class);

  private final ModuleServiceClient moduleServiceClient;

  public UserModuleController(ModuleServiceClient moduleServiceClient) {
    this.moduleServiceClient = moduleServiceClient;
  }

  @PutMapping("/{userId}/modules/{moduleId}")
  public ResponseEntity<Void> assignModule(@PathVariable UUID userId,
      @PathVariable UUID moduleId) {

    if (!moduleServiceClient.isAvailable(moduleId)) {
      throw new ModuleNotFoundException(moduleId);
    }
    moduleServiceClient.assign(userId, moduleId);
    log.info("Assigned module {} to user {}", moduleId, userId);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(ModuleNotFoundException.class)
  public ResponseEntity<ErrorPayload> handleNotFound(ModuleNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorPayload("MODULE_NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(ModuleServiceUnavailableException.class)
  public ResponseEntity<ErrorPayload> handleUnavailable(ModuleServiceUnavailableException ex) {
    log.warn("module_service unavailable: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorPayload("MODULE_SERVICE_UNAVAILABLE", ex.getMessage()));
  }

  public record ErrorPayload(String code, String message) {
  }
}
