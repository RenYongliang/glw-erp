/** 产品主数据在筛选弹窗中的级联选项：品牌 → 型号 → 封装。 */
export interface ProductFilterOption {
  brand: string
  partNo: string
  packageType: string
}

/** 把后端产品选项（name 为型号、model 为封装）规范成筛选用的字段。 */
export function productFilterOptions(
  rows: { brand?: string; name: string; model?: string }[],
): ProductFilterOption[] {
  return rows.map((row) => ({
    brand: row.brand ?? '',
    partNo: row.name,
    packageType: row.model ?? '',
  }))
}

const unique = (values: string[]) => [...new Set(values.filter(Boolean))]

export function brandOptions(rows: ProductFilterOption[]): string[] {
  return unique(rows.map((row) => row.brand))
}

export function partNoOptions(rows: ProductFilterOption[], brand = ''): string[] {
  return unique(
    rows.filter((row) => !brand || row.brand === brand).map((row) => row.partNo),
  )
}

export function packageTypeOptions(
  rows: ProductFilterOption[],
  brand = '',
  partNo = '',
): string[] {
  return unique(
    rows
      .filter(
        (row) =>
          (!brand || row.brand === brand) && (!partNo || row.partNo === partNo),
      )
      .map((row) => row.packageType),
  )
}
