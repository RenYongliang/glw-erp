package com.glw.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.mapper.BrandMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.mapper.SupplierBrandMapper;
import com.glw.backend.model.dto.BrandSaveDTO;
import com.glw.backend.model.dto.PageDTO;
import com.glw.backend.model.entity.Brand;
import com.glw.backend.model.entity.Product;
import com.glw.backend.model.entity.SupplierBrand;
import com.glw.backend.model.vo.BrandOptionVO;
import com.glw.backend.model.vo.BrandVO;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 品牌主数据：品牌属于原厂，供应商通过 {@code supplier_brand} 维护可供品牌。
 *
 * <p>品牌被供应商关联或产品引用时不允许删除；改名时同步产品上的冗余品牌名称。
 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class BrandManagementService {
    private final BrandMapper brands;
    private final SupplierBrandMapper supplierBrands;
    private final ProductMapper products;

    public IPage<BrandVO> page(PageDTO query) {
        return brands.selectPage(query.toPage(),
                        new LambdaQueryWrapper<Brand>().orderByAsc(Brand::getId))
                .convert(brand -> toVO(brand));
    }

    public List<BrandOptionVO> options() {
        return brands.selectList(new LambdaQueryWrapper<Brand>()
                        .eq(Brand::getEnabled, true).orderByAsc(Brand::getName))
                .stream()
                .map(brand -> new BrandOptionVO(brand.getId(), brand.getCode(),
                        brand.getName(), brand.getManufacturer()))
                .toList();
    }

    @Transactional
    public BrandVO saveBrand(Long id, BrandSaveDTO input) {
        Brand existing = id == null ? null : requireBrand(id);
        String name = input.getName().trim();
        String code = normalize(input.getCode());
        if (brands.selectCount(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getName, name).ne(id != null, Brand::getId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "品牌名称已存在");
        if (code != null && brands.selectCount(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getCode, code).ne(id != null, Brand::getId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "品牌编码已存在");

        var brand = new Brand();
        BeanUtils.copyProperties(input, brand);
        brand.setName(name);
        brand.setCode(code);
        brand.setEnabled(input.getEnabled() == null || input.getEnabled());
        brand.setId(id);
        if (id == null) {
            brand.setVersion(0);
            brands.insert(brand);
        } else {
            requireVersion(input.getVersion());
            // 显式 set 允许清空可选字段，version 同时作为并发修改保护。
            int changed = brands.update(null, new LambdaUpdateWrapper<Brand>()
                    .eq(Brand::getId, id).eq(Brand::getVersion, input.getVersion())
                    .set(Brand::getCode, code).set(Brand::getName, name)
                    .set(Brand::getManufacturer, input.getManufacturer())
                    .set(Brand::getEnabled, brand.getEnabled())
                    .set(Brand::getRemark, input.getRemark())
                    .set(Brand::getVersion, input.getVersion() + 1));
            requireChanged(changed);
            // product.brand 是展示冗余字段，品牌改名时同步，避免新旧名称并存。
            if (!name.equals(existing.getName()))
                products.update(null, new LambdaUpdateWrapper<Product>()
                        .eq(Product::getBrandId, id).set(Product::getBrand, name));
        }
        return toVO(brands.selectById(brand.getId()));
    }

    @Transactional
    public void deleteBrand(Long id, Integer version) {
        requireBrand(id);
        requireVersion(version);
        if (supplierBrands.selectCount(new LambdaQueryWrapper<SupplierBrand>()
                .eq(SupplierBrand::getBrandId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "品牌已关联供应商，不能删除");
        if (products.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getBrandId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "品牌已有产品，不能删除");
        requireChanged(brands.delete(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getId, id).eq(Brand::getVersion, version)));
    }

    private Brand requireBrand(Long id) {
        Brand brand = brands.selectById(id);
        if (brand == null) throw new BusinessException(ErrorCode.NOT_FOUND, "品牌不存在");
        return brand;
    }

    private static BrandVO toVO(Brand brand) {
        return new BrandVO(brand.getId(), brand.getCode(), brand.getName(),
                brand.getManufacturer(), brand.getEnabled(), brand.getRemark(),
                brand.getVersion());
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireVersion(Integer version) {
        if (version == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少数据版本，请刷新后重试");
    }

    private static void requireChanged(int count) {
        if (count != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "记录已被修改，请刷新后重试");
    }
}
