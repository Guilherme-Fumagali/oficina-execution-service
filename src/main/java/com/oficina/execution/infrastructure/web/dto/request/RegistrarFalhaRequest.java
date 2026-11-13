package com.oficina.execution.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarFalhaRequest(@NotBlank @Size(max = 500) String motivo) {}
