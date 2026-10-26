package com.glw.backend;

import com.glw.backend.common.BusinessException;
import com.glw.backend.mapper.*;
import com.glw.backend.model.dto.*;
import com.glw.backend.model.entity.*;
import com.glw.backend.model.enums.TaxIncluded;
import com.glw.backend.service.CrmManagementService;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CrmManagementServiceTest {
    @BeforeAll static void initializeLambdaMetadata() {
        var configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "crm-customer"), Customer.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "crm-visit"), CustomerVisit.class);
    }
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final CustomerVisitMapper visits = mock(CustomerVisitMapper.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final SalesOrderMapper sales = mock(SalesOrderMapper.class);
    private final OutboundOrderMapper outbound = mock(OutboundOrderMapper.class);
    private final SampleApplyMapper samples = mock(SampleApplyMapper.class);
    private final CrmManagementService service = new CrmManagementService(customers, visits, products, sales, outbound, samples);

    @Test void preventsDeletingCustomerWithBusinessRecords() {
        when(customers.selectById(1L)).thenReturn(new Customer());
        when(visits.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.deleteCustomer(1L, 0)).isInstanceOf(BusinessException.class).hasMessageContaining("不能删除");
        verify(customers, never()).delete(any());
    }

    @Test void refusesVisitWithMissingCustomerOrProduct() {
        var input = visit();
        assertThatThrownBy(() -> service.saveVisit(null, input)).hasMessage("客户不存在");
        when(customers.selectById(1L)).thenReturn(new Customer());
        input.setProductId(9L);
        assertThatThrownBy(() -> service.saveVisit(null, input)).hasMessage("推荐产品不存在");
        verify(visits, never()).insert(any(CustomerVisit.class));
    }

    @Test void detectsStaleCustomerAndVisitEdits() {
        when(customers.selectById(1L)).thenReturn(new Customer());
        when(visits.selectById(2L)).thenReturn(new CustomerVisit());
        var customer = new CustomerSaveDTO();
        customer.setName("Example"); customer.setVersion(3);
        assertThatThrownBy(() -> service.saveCustomer(1L, customer)).hasMessageContaining("记录已被修改");
        var visit = visit(); visit.setVersion(3);
        assertThatThrownBy(() -> service.saveVisit(2L, visit)).hasMessageContaining("记录已被修改");
    }

    @Test void quoteValidationKeepsFourDecimalPricesAndRejectsInvalidValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var input = visit();
            assertThat(validator.validate(input)).isEmpty();
            input.setUnitPrice(new BigDecimal("0.07501"));
            assertThat(validator.validate(input)).isNotEmpty();
            input.setUnitPrice(new BigDecimal("-0.0750"));
            assertThat(validator.validate(input)).isNotEmpty();
        }
    }

    private CustomerVisitSaveDTO visit() {
        var input = new CustomerVisitSaveDTO();
        input.setCustomerId(1L); input.setVisitTime(LocalDateTime.of(2026, 10, 26, 9, 0));
        input.setUnitPrice(new BigDecimal("0.0750")); input.setTaxIncluded(TaxIncluded.INCLUDED);
        return input;
    }
}
