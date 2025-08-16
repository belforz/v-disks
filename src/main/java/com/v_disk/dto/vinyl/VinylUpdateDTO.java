package com.v_disk.dto.vinyl;

import java.math.BigDecimal;
import java.util.List;

public record VinylUpdateDTO(
    String title,
    String artist,
    BigDecimal price,
    Integer stock,
    String coverPath,
    List<String> gallery
) {}
