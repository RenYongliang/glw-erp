package com.glw.backend.service;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.mapper.DocumentNumberMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@RequiredArgsConstructor
public class DocumentNumberService {
    public enum Kind {
        SALES("SO", "sales_order", "order_no"),
        PURCHASE("GL", "purchase_order", "purchase_order_no"),
        INBOUND("IN", "inbound_order", "inbound_order_no"),
        OUTBOUND("OUT", "outbound_order", "outbound_order_no"),
        STATEMENT_AR("AR", "fin_statement", "statement_no"),
        STATEMENT_AP("AP", "fin_statement", "statement_no"),
        RECEIPT("RC", "fin_payment", "payment_no"),
        PAYMENT("PY", "fin_payment", "payment_no");

        final String prefix;
        final String table;
        final String column;
        Kind(String prefix, String table, String column) {
            this.prefix = prefix;
            this.table = table;
            this.column = column;
        }
    }

    private final DocumentNumberMapper mapper;

    // The counter lock is held until the document transaction commits or rolls back.
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Kind kind, LocalDateTime businessTime) {
        LocalDate date = (businessTime == null ? LocalDateTime.now() : businessTime).toLocalDate();
        String prefix = kind.prefix + date.format(DateTimeFormatter.BASIC_ISO_DATE);
        mapper.ensureCounter(kind.prefix, date);
        int current = mapper.lockCounter(kind.prefix, date);
        int next = Math.max(current, mapper.existingMax(kind.table, kind.column, prefix)) + 1;
        if (next > 999) throw new BusinessException(ErrorCode.CONFLICT, "该类型当日单据流水号已用完（最多 999 单）");
        mapper.updateCounter(kind.prefix, date, next);
        return prefix + String.format("%03d", next);
    }
}
