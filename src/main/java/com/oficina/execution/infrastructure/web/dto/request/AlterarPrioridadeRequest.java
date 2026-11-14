package com.oficina.execution.infrastructure.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AlterarPrioridadeRequest(@NotNull @Min(0) @Max(100) Integer prioridade) {}
