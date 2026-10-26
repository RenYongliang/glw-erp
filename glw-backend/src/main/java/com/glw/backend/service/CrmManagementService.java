package com.glw.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.mapper.*;
import com.glw.backend.model.dto.*;
import com.glw.backend.model.entity.*;
import com.glw.backend.model.vo.CustomerVisitVO;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Profile("!mock")
@AllArgsConstructor
public class CrmManagementService {
    private final CustomerMapper customers;
    private final CustomerVisitMapper visits;
    private final ProductMapper products;
    private final SalesOrderMapper sales;
    private final OutboundOrderMapper outbound;
    private final SampleApplyMapper samples;

    public IPage<CustomerVisitVO> visits(PageDTO query) {
        IPage<CustomerVisit> page = visits.selectPage(query.toPage(), new LambdaQueryWrapper<CustomerVisit>()
                .orderByDesc(CustomerVisit::getVisitTime).orderByDesc(CustomerVisit::getId));
        var customerIds = page.getRecords().stream().map(CustomerVisit::getCustomerId).distinct().toList();
        var productIds = page.getRecords().stream().map(CustomerVisit::getProductId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Customer> customerMap = customerIds.isEmpty() ? Map.of() : customers.selectByIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));
        Map<Long, Product> productMap = productIds.isEmpty() ? Map.of() : products.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        return page.convert(visit -> {
            var result = new CustomerVisitVO();
            BeanUtils.copyProperties(visit, result);
            var customer = customerMap.get(visit.getCustomerId());
            if (customer != null) {
                result.setCustomerName(customer.getName());
                result.setCustomerShortName(customer.getShortName());
            }
            var product = visit.getProductId() == null ? null : productMap.get(visit.getProductId());
            if (product != null) {
                result.setBrand(product.getBrand());
                result.setPartNo(product.getPartNo());
                result.setPackageType(product.getPackageType());
            }
            return result;
        });
    }

    @Transactional
    public Customer saveCustomer(Long id, CustomerSaveDTO input) {
        if (id != null) requireCustomer(id);
        String name = input.getName().trim();
        if (customers.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getName, name)
                .ne(id != null, Customer::getId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "客户名称已存在");
        var customer = new Customer();
        BeanUtils.copyProperties(input, customer);
        customer.setName(name);
        customer.setId(id);
        if (id == null) {
            customer.setVersion(0);
            customers.insert(customer);
        } else {
            requireVersion(input.getVersion());
            // Explicit setters allow optional fields to be cleared, while the version guards concurrent edits.
            int changed = customers.update(null, new LambdaUpdateWrapper<Customer>()
                    .eq(Customer::getId, id).eq(Customer::getVersion, input.getVersion())
                    .set(Customer::getName, name).set(Customer::getShortName, input.getShortName())
                    .set(Customer::getCategory, input.getCategory())
                    .set(Customer::getAddress, input.getAddress()).set(Customer::getContact, input.getContact())
                    .set(Customer::getContactPhone, input.getContactPhone()).set(Customer::getSettleType, input.getSettleType())
                    .set(Customer::getVersion, input.getVersion() + 1));
            requireChanged(changed);
        }
        return customers.selectById(customer.getId());
    }

    @Transactional
    public CustomerVisit saveVisit(Long id, CustomerVisitSaveDTO input) {
        requireCustomer(input.getCustomerId());
        if (input.getProductId() != null && products.selectById(input.getProductId()) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "推荐产品不存在");
        var visit = new CustomerVisit();
        BeanUtils.copyProperties(input, visit);
        visit.setId(id);
        if (id == null) {
            visit.setVersion(0);
            visits.insert(visit);
        } else {
            requireVisit(id);
            requireVersion(input.getVersion());
            int changed = visits.update(null, new LambdaUpdateWrapper<CustomerVisit>()
                    .eq(CustomerVisit::getId, id).eq(CustomerVisit::getVersion, input.getVersion())
                    .set(CustomerVisit::getCustomerId, input.getCustomerId()).set(CustomerVisit::getVisitTime, input.getVisitTime())
                    .set(CustomerVisit::getProject, input.getProject()).set(CustomerVisit::getOriginalPartNo, input.getOriginalPartNo())
                    .set(CustomerVisit::getProductId, input.getProductId()).set(CustomerVisit::getDemandQuantity, input.getDemandQuantity())
                    .set(CustomerVisit::getUnitPrice, input.getUnitPrice()).set(CustomerVisit::getTaxIncluded, input.getTaxIncluded())
                    .set(CustomerVisit::getProgress, input.getProgress()).set(CustomerVisit::getDetail, input.getDetail())
                    .set(CustomerVisit::getStage, input.getStage())
                    .set(CustomerVisit::getVersion, input.getVersion() + 1));
            requireChanged(changed);
        }
        return visits.selectById(visit.getId());
    }

    @Transactional
    public void deleteCustomer(Long id, Integer version) {
        requireCustomer(id);
        requireVersion(version);
        if (visits.selectCount(new LambdaQueryWrapper<CustomerVisit>().eq(CustomerVisit::getCustomerId, id)) > 0
                || sales.selectCount(new LambdaQueryWrapper<SalesOrder>().eq(SalesOrder::getCustomerId, id)) > 0
                || outbound.selectCount(new LambdaQueryWrapper<OutboundOrder>().eq(OutboundOrder::getCustomerId, id)) > 0
                || samples.selectCount(new LambdaQueryWrapper<SampleApply>().eq(SampleApply::getCustomerId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "客户已有跟进、销售、出库或送样记录，不能删除");
        requireChanged(customers.delete(new LambdaQueryWrapper<Customer>().eq(Customer::getId, id).eq(Customer::getVersion, version)));
    }

    @Transactional
    public void deleteVisit(Long id, Integer version) {
        requireVisit(id);
        requireVersion(version);
        requireChanged(visits.delete(new LambdaQueryWrapper<CustomerVisit>().eq(CustomerVisit::getId, id).eq(CustomerVisit::getVersion, version)));
    }

    private void requireCustomer(Long id) {
        if (customers.selectById(id) == null) throw new BusinessException(ErrorCode.NOT_FOUND, "客户不存在");
    }
    private void requireVisit(Long id) {
        if (visits.selectById(id) == null) throw new BusinessException(ErrorCode.NOT_FOUND, "跟进记录不存在");
    }
    private void requireVersion(Integer version) {
        if (version == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少数据版本，请刷新后重试");
    }
    private void requireChanged(int count) {
        if (count != 1) throw new BusinessException(ErrorCode.CONFLICT, "记录已被修改，请刷新后重试");
    }
}
