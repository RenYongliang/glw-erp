package com.glw.backend.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Param;

public interface DocumentNumberMapper {
    void ensureCounter(@Param("type") String type, @Param("date") LocalDate date);

    int lockCounter(@Param("type") String type, @Param("date") LocalDate date);

    // Identifiers are supplied exclusively by DocumentNumberService.Kind, never by request input.
    int existingMax(@Param("table") String table, @Param("column") String column, @Param("prefix") String prefix);

    void updateCounter(@Param("type") String type, @Param("date") LocalDate date, @Param("sequence") int sequence);
}
