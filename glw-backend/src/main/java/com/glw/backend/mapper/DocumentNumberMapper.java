package com.glw.backend.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DocumentNumberMapper {
    @Insert("INSERT INTO document_number_sequence (document_type, business_date, last_sequence) "
            + "VALUES (#{type}, #{date}, 0) ON DUPLICATE KEY UPDATE document_type = document_type")
    void ensureCounter(@Param("type") String type, @Param("date") LocalDate date);

    @Select("SELECT last_sequence FROM document_number_sequence "
            + "WHERE document_type = #{type} AND business_date = #{date} FOR UPDATE")
    int lockCounter(@Param("type") String type, @Param("date") LocalDate date);

    // Identifiers are supplied exclusively by DocumentNumberService.Kind, never by request input.
    @Select("SELECT COALESCE(MAX(CAST(RIGHT(`${column}`, 3) AS UNSIGNED)), 0) FROM `${table}` "
            + "WHERE `${column}` REGEXP CONCAT('^', #{prefix}, '[0-9]{3}$')")
    int existingMax(@Param("table") String table, @Param("column") String column, @Param("prefix") String prefix);

    @Update("UPDATE document_number_sequence SET last_sequence = #{sequence} "
            + "WHERE document_type = #{type} AND business_date = #{date}")
    void updateCounter(@Param("type") String type, @Param("date") LocalDate date, @Param("sequence") int sequence);
}
