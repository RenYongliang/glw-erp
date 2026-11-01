package com.glw.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.mapper.BrandMapper;
import com.glw.backend.mapper.FinOpenItemMapper;
import com.glw.backend.mapper.FinProfileMapper;
import com.glw.backend.mapper.InboundOrderMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.mapper.PurchaseOrderMapper;
import com.glw.backend.mapper.SupplierBrandMapper;
import com.glw.backend.mapper.SupplierMapper;
import com.glw.backend.model.dto.PageDTO;
import com.glw.backend.model.dto.SupplierBrandSaveDTO;
import com.glw.backend.model.dto.SupplierSaveDTO;
import com.glw.backend.model.entity.Brand;
import com.glw.backend.model.entity.FinOpenItem;
import com.glw.backend.model.entity.FinProfile;
import com.glw.backend.model.entity.InboundOrder;
import com.glw.backend.model.entity.Product;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.entity.Supplier;
import com.glw.backend.model.entity.SupplierBrand;
import com.glw.backend.model.vo.SupplierBrandVO;
import com.glw.backend.model.vo.SupplierOptionVO;
import com.glw.backend.model.vo.SupplierVO;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 供应商主数据：供应商、供应商品牌。
 *
 * <p>品牌主数据在 {@code brand}，供应商与品牌通过 {@code supplier_brand} 多对多关联；
 * 删除供应商时先校验产品、采购、入库和应付记录，没有引用才连品牌关联一起删除。
 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class SupplierManagementService {
    private final SupplierMapper suppliers;
    private final SupplierBrandMapper supplierBrands;
    private final BrandMapper brands;
    private final ProductMapper products;
    private final PurchaseOrderMapper purchaseOrders;
    private final InboundOrderMapper inboundOrders;
    private final FinProfileMapper profiles;
    private final FinOpenItemMapper openItems;

    public IPage<SupplierVO> page(PageDTO query) {
        IPage<Supplier> page = suppliers.selectPage(query.toPage(),
                new LambdaQueryWrapper<Supplier>().orderByAsc(Supplier::getId));
        Map<Long, List<String>> brands = brandsFor(page.getRecords().stream()
                .map(Supplier::getId).toList());
        return page.convert(supplier -> toVO(supplier,
                brands.getOrDefault(supplier.getId(), List.of())));
    }

    public List<SupplierOptionVO> options() {
        return suppliers.selectList(new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getEnabled, true).orderByAsc(Supplier::getId))
                .stream()
                .map(supplier -> new SupplierOptionVO(supplier.getId(), supplier.getCode(),
                        supplier.getName(), supplier.getShortName()))
                .toList();
    }

    @Transactional
    public SupplierVO saveSupplier(Long id, SupplierSaveDTO input) {
        if (id != null) requireSupplier(id);
        String name = input.getName().trim();
        String code = normalize(input.getCode());
        if (suppliers.selectCount(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, name).ne(id != null, Supplier::getId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "供应商名称已存在");
        if (code != null && suppliers.selectCount(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getCode, code).ne(id != null, Supplier::getId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "供应商编码已存在");

        var supplier = new Supplier();
        BeanUtils.copyProperties(input, supplier);
        supplier.setName(name);
        supplier.setCode(code);
        supplier.setEnabled(input.getEnabled() == null || input.getEnabled());
        supplier.setId(id);
        if (id == null) {
            supplier.setVersion(0);
            suppliers.insert(supplier);
        } else {
            requireVersion(input.getVersion());
            // 显式 set 允许清空可选字段，version 同时作为并发修改保护。
            int changed = suppliers.update(null, new LambdaUpdateWrapper<Supplier>()
                    .eq(Supplier::getId, id).eq(Supplier::getVersion, input.getVersion())
                    .set(Supplier::getCode, code).set(Supplier::getName, name)
                    .set(Supplier::getShortName, input.getShortName())
                    .set(Supplier::getCategory, input.getCategory())
                    .set(Supplier::getEnabled, supplier.getEnabled())
                    .set(Supplier::getAddress, input.getAddress())
                    .set(Supplier::getContact, input.getContact())
                    .set(Supplier::getContactPhone, input.getContactPhone())
                    .set(Supplier::getTaxNo, input.getTaxNo())
                    .set(Supplier::getBankName, input.getBankName())
                    .set(Supplier::getBankAccount, input.getBankAccount())
                    .set(Supplier::getAccountName, input.getAccountName())
                    .set(Supplier::getRemark, input.getRemark())
                    .set(Supplier::getVersion, input.getVersion() + 1));
            requireChanged(changed);
        }
        Supplier saved = suppliers.selectById(supplier.getId());
        return toVO(saved, brandsFor(List.of(saved.getId())).getOrDefault(saved.getId(), List.of()));
    }

    @Transactional
    public void deleteSupplier(Long id, Integer version) {
        requireSupplier(id);
        requireVersion(version);
        if (products.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getSupplierId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "供应商已有产品，不能删除");
        if (purchaseOrders.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getSupplierId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "供应商已有采购订单，不能删除");
        if (inboundOrders.selectCount(new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getSupplierId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "供应商已有入库单，不能删除");
        if (profiles.selectCount(new LambdaQueryWrapper<FinProfile>()
                .eq(FinProfile::getDirection, "AP").eq(FinProfile::getPartnerId, id)) > 0
                || openItems.selectCount(new LambdaQueryWrapper<FinOpenItem>()
                .eq(FinOpenItem::getDirection, "AP").eq(FinOpenItem::getPartnerId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "供应商已有应付记录，不能删除");
        supplierBrands.delete(new LambdaQueryWrapper<SupplierBrand>()
                .eq(SupplierBrand::getSupplierId, id));
        requireChanged(suppliers.delete(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getId, id).eq(Supplier::getVersion, version)));
    }

    public List<SupplierBrandVO> brands(Long supplierId, String keyword) {
        String search = normalize(keyword);
        List<Long> matchedBrandIds = null;
        if (search != null) {
            matchedBrandIds = brands.selectList(new LambdaQueryWrapper<Brand>()
                            .like(Brand::getName, search)).stream()
                    .map(Brand::getId).toList();
            if (matchedBrandIds.isEmpty()) return List.of();
        }
        List<SupplierBrand> rows = supplierBrands.selectList(new LambdaQueryWrapper<SupplierBrand>()
                .eq(supplierId != null, SupplierBrand::getSupplierId, supplierId)
                .in(matchedBrandIds != null, SupplierBrand::getBrandId, matchedBrandIds)
                .orderByAsc(SupplierBrand::getSupplierId));
        Map<Long, String> names = supplierNames(rows.stream()
                .map(SupplierBrand::getSupplierId).distinct().toList());
        Map<Long, String> brandNames = brandNames(rows.stream()
                .map(SupplierBrand::getBrandId).distinct().toList());
        return rows.stream()
                .map(row -> new SupplierBrandVO(row.getId(), row.getSupplierId(),
                        names.getOrDefault(row.getSupplierId(), ""), row.getBrandId(),
                        brandNames.getOrDefault(row.getBrandId(), ""), row.getRemark(),
                        row.getVersion()))
                .sorted(Comparator.comparing(SupplierBrandVO::supplierName)
                        .thenComparing(SupplierBrandVO::brandName))
                .toList();
    }

    @Transactional
    public SupplierBrandVO saveBrand(Long id, SupplierBrandSaveDTO input) {
        requireSupplier(input.getSupplierId());
        Brand brand = brands.selectById(input.getBrandId());
        if (brand == null) throw new BusinessException(ErrorCode.NOT_FOUND, "品牌不存在");
        if (supplierBrands.selectCount(new LambdaQueryWrapper<SupplierBrand>()
                .eq(SupplierBrand::getSupplierId, input.getSupplierId())
                .eq(SupplierBrand::getBrandId, input.getBrandId())
                .ne(id != null, SupplierBrand::getId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "该供应商已关联此品牌");

        var row = new SupplierBrand();
        BeanUtils.copyProperties(input, row);
        row.setId(id);
        if (id == null) {
            row.setVersion(0);
            supplierBrands.insert(row);
        } else {
            requireVersion(input.getVersion());
            int changed = supplierBrands.update(null, new LambdaUpdateWrapper<SupplierBrand>()
                    .eq(SupplierBrand::getId, id).eq(SupplierBrand::getVersion, input.getVersion())
                    .set(SupplierBrand::getSupplierId, input.getSupplierId())
                    .set(SupplierBrand::getBrandId, input.getBrandId())
                    .set(SupplierBrand::getRemark, input.getRemark())
                    .set(SupplierBrand::getVersion, input.getVersion() + 1));
            requireChanged(changed);
        }
        SupplierBrand saved = supplierBrands.selectById(row.getId());
        Supplier supplier = suppliers.selectById(saved.getSupplierId());
        return new SupplierBrandVO(saved.getId(), saved.getSupplierId(), supplier.getName(),
                saved.getBrandId(), brandNames(List.of(saved.getBrandId()))
                        .getOrDefault(saved.getBrandId(), ""), saved.getRemark(),
                saved.getVersion());
    }

    @Transactional
    public void deleteBrand(Long id, Integer version) {
        if (supplierBrands.selectById(id) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商品牌不存在");
        requireVersion(version);
        requireChanged(supplierBrands.delete(new LambdaQueryWrapper<SupplierBrand>()
                .eq(SupplierBrand::getId, id).eq(SupplierBrand::getVersion, version)));
    }

    private Map<Long, List<String>> brandsFor(Collection<Long> supplierIds) {
        if (supplierIds.isEmpty()) return Map.of();
        List<SupplierBrand> rows = supplierBrands.selectList(new LambdaQueryWrapper<SupplierBrand>()
                .in(SupplierBrand::getSupplierId, supplierIds));
        Map<Long, String> names = brandNames(rows.stream()
                .map(SupplierBrand::getBrandId).distinct().toList());
        return rows.stream()
                .sorted(Comparator.comparing(row -> names.getOrDefault(row.getBrandId(), "")))
                .collect(Collectors.groupingBy(SupplierBrand::getSupplierId,
                        Collectors.mapping(row -> names.getOrDefault(row.getBrandId(), ""),
                                Collectors.toList())));
    }

    private Map<Long, String> brandNames(Collection<Long> brandIds) {
        if (brandIds.isEmpty()) return Map.of();
        return brands.selectByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Brand::getName));
    }

    private Map<Long, String> supplierNames(Collection<Long> supplierIds) {
        if (supplierIds.isEmpty()) return Map.of();
        return suppliers.selectByIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }

    private static SupplierVO toVO(Supplier supplier, List<String> brands) {
        return new SupplierVO(supplier.getId(), supplier.getCode(), supplier.getName(),
                supplier.getShortName(), supplier.getCategory(), supplier.getEnabled(),
                supplier.getAddress(), supplier.getContact(), supplier.getContactPhone(),
                supplier.getTaxNo(), supplier.getBankName(), supplier.getBankAccount(),
                supplier.getAccountName(), supplier.getRemark(), supplier.getVersion(), brands);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireSupplier(Long id) {
        if (suppliers.selectById(id) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
    }

    private void requireVersion(Integer version) {
        if (version == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少数据版本，请刷新后重试");
    }

    private void requireChanged(int count) {
        if (count != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被修改，请刷新后重试");
    }
}
