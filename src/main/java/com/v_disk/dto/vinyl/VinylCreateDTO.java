package com.v_disk.dto.vinyl;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VinylCreateDTO(
    @NotBlank String title,
    @NotBlank String artist,
    @NotNull @DecimalMin("0.0") BigDecimal price,
    @NotNull @Min(0) Integer stock,
    @NotBlank String coverPath,
    List<@NotBlank String> gallery,
    Boolean isPrincipal
) {}

