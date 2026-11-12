package com.oficina.execution.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarApontamentoRequest(@NotBlank @Size(max = 2000) String texto) {}
