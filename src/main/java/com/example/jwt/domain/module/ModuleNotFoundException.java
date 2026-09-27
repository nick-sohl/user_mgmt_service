package com.example.jwt.domain.module;

import java.util.UUID;

public class ModuleNotFoundException extends RuntimeException {

  public ModuleNotFoundException(UUID moduleId) {
    super("Module " + moduleId + " is not available");
  }
}
