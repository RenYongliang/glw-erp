package com.glw.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.mapper.CustomerMapper;
import com.glw.backend.mapper.CustomerProductCodeMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.model.dto.CustomerProductCodeSaveDTO;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.model.entity.CustomerProductCode;
import com.glw.backend.model.entity.Product;
import com.glw.backend.model.vo.CustomerProductCodeVO;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客户物料编码对照：同一客户对同一产品保留一个客户系统内的物料编码。
 *
 * <p>销售订单保存时会快照到订单行，发货单优先打印订单行上的客户编码。
 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class CustomerProductCodeManagementService {
    private final CustomerProductCodeMapper codes;
    private final CustomerMapper customers;
    private final ProductMapper products;

    public List<CustomerProductCodeVO> list(Long customerId) {
        requireCustomer(customerId);
        return codes.selectByCustomer(customerId);
    }

    @Transactional
    public CustomerProductCodeVO save(Long id, CustomerProductCodeSaveDTO input) {
        if (id == null) return create(input);
        CustomerProductCode existing = codes.selectById(id);
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "客户物料编码不存在");
        if (input.getVersion() == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少数据版本，请刷新后重试");
        Product product = requireProduct(input.getProductId());
        if (!product.getId().equals(existing.getProductId()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能修改已维护的产品，请删除后重新添加");
        String code = normalizeCode(input.getCustomerCode());
        requireUnique(existing.getCustomerId(), product.getId(), code, id);

        try {
            requireChanged(codes.update(null, new LambdaUpdateWrapper<CustomerProductCode>()
                    .eq(CustomerProductCode::getId, id)
                    .eq(CustomerProductCode::getVersion, input.getVersion())
                    .set(CustomerProductCode::getCustomerCode, code)
                    .set(CustomerProductCode::getEnabled, input.getEnabled() == null || input.getEnabled())
                    .set(CustomerProductCode::getRemark, normalizeText(input.getRemark()))
                    .set(CustomerProductCode::getVersion, input.getVersion() + 1)));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "该客户物料编码已存在");
        }
        return toVO(codes.selectById(id), product);
    }

    @Transactional
    public void delete(Long id, Integer version) {
        if (codes.selectById(id) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "客户物料编码不存在");
        if (version == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少数据版本，请刷新后重试");
        requireChanged(codes.delete(new LambdaQueryWrapper<CustomerProductCode>()
                .eq(CustomerProductCode::getId, id)
                .eq(CustomerProductCode::getVersion, version)));
    }

    private CustomerProductCodeVO create(CustomerProductCodeSaveDTO input) {
        if (input.getCustomerId() == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少客户");
        requireCustomer(input.getCustomerId());
        Product product = requireProduct(input.getProductId());
        String code = normalizeCode(input.getCustomerCode());
        requireUnique(input.getCustomerId(), product.getId(), code, null);

        var row = new CustomerProductCode();
        row.setCustomerId(input.getCustomerId());
        row.setProductId(product.getId());
        row.setCustomerCode(code);
        row.setSource("MANUAL");
        row.setEnabled(input.getEnabled() == null || input.getEnabled());
        row.setRemark(normalizeText(input.getRemark()));
        row.setVersion(0);
        try {
            codes.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "该客户已维护此产品的物料编码");
        }
        return toVO(codes.selectById(row.getId()), product);
    }

    private void requireUnique(Long customerId, Long productId, String customerCode, Long excludeId) {
        if (codes.selectCount(new LambdaQueryWrapper<CustomerProductCode>()
                .eq(CustomerProductCode::getCustomerId, customerId)
                .eq(CustomerProductCode::getProductId, productId)
                .ne(excludeId != null, CustomerProductCode::getId, excludeId)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "该客户已维护此产品的物料编码");
        if (codes.selectCount(new LambdaQueryWrapper<CustomerProductCode>()
                .eq(CustomerProductCode::getCustomerId, customerId)
                .eq(CustomerProductCode::getCustomerCode, customerCode)
                .ne(excludeId != null, CustomerProductCode::getId, excludeId)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "该客户物料编码已存在");
    }

    private void requireCustomer(Long customerId) {
        Customer customer = customerId == null ? null : customers.selectById(customerId);
        if (customer == null) throw new BusinessException(ErrorCode.NOT_FOUND, "客户不存在");
    }

    private Product requireProduct(Long productId) {
        Product product = productId == null ? null : products.selectById(productId);
        if (product == null) throw new BusinessException(ErrorCode.NOT_FOUND, "产品不存在");
        return product;
    }

    private static CustomerProductCodeVO toVO(CustomerProductCode row, Product product) {
        var vo = new CustomerProductCodeVO();
        vo.setId(row.getId());
        vo.setCustomerId(row.getCustomerId());
        vo.setProductId(row.getProductId());
        vo.setBrand(product.getBrand());
        vo.setPartNo(product.getPartNo());
        vo.setPackageType(product.getPackageType());
        vo.setCustomerCode(row.getCustomerCode());
        vo.setSource(row.getSource());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        return vo;
    }

    private static String normalizeCode(String value) {
        String text = normalizeText(value);
        if (text == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "客户物料编码不能为空");
        if (text.length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "客户物料编码不能超过 64 字");
        return text;
    }

    private static String normalizeText(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static void requireChanged(int count) {
        if (count != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被修改，请刷新后重试");
    }
}
