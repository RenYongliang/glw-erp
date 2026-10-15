"""Generate a reviewed, empty-database MySQL import. Requires openpyxl."""

import json
import re
from collections import defaultdict
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import openpyxl


ROOT = Path(__file__).resolve().parent
TABLES = {}
ISSUES = []
TRACE = []
CENT = Decimal('0.01')


def note(sheet, row, message):
    ISSUES.append(f'{sheet}!{row}: {message}')


def add(table, **values):
    records = TABLES.setdefault(table, [])
    record = dict(id=len(records) + 1, **values)
    records.append(record)
    return record


def text(value):
    return str(value).strip() if value is not None else None


def money(value):
    return Decimal(str(value)).quantize(CENT, rounding=ROUND_HALF_UP)


def price(value):
    match = re.search(r'\d+(?:\.\d+)?', str(value))
    if not match:
        raise ValueError(f'Invalid price: {value!r}')
    return money(match[0]), 0 if '未税' in str(value) else 1


def rows(sheet, start):
    # Only merged cells inherit values. Ordinary blanks remain unknown.
    merged = {}
    for region in sheet.merged_cells.ranges:
        for row in sheet.iter_rows(min_row=region.min_row, max_row=region.max_row,
                                   min_col=region.min_col, max_col=region.max_col):
            for cell in row:
                merged[cell.coordinate] = sheet.cell(region.min_row, region.min_col).value
    for row in sheet.iter_rows(min_row=start):
        yield row[0].row, [merged.get(c.coordinate, c.value) for c in row]


workbook = openpyxl.load_workbook(ROOT / '表格.xlsx', data_only=True)
raw = {s.title: [{'row': i, 'values': list(r)} for i, r in enumerate(s.values, 1)
                 if any(v is not None for v in r)] for s in workbook}
customers, suppliers, products = {}, {}, {}
aliases = {
    '深圳成胜微': '深圳市成胜微电子有限公司',
    '广州巨大汽车音响': '广州番禺巨大汽车音响设备有限公司',
    '深圳市铭泰电子': '深圳市铭泰电子信息科技有限公司',
    '深圳市勤泰信息': '深圳市勤泰智能信息技术有限公司',
    '深圳中诺泽': '深圳市中诺泽科技有限公司',
    '勤泰信息': '深圳市勤泰智能信息技术有限公司',
    '铭泰科技': '深圳市铭泰电子信息科技有限公司',
    '阿达视': '深圳市阿达视高新技术有限公司',
    '莞市慧思电子有限公司': '东莞市慧思电子有限公司',
}


def customer(name, terms=None, contact=None):
    name = text(name).split('\n')[0]
    canonical = aliases.get(name, name)
    if canonical not in customers:
        customers[canonical] = add('customer', name=canonical, short_name=None,
                                   contact=None, settle_type=None)
    c = customers[canonical]
    if name != canonical and not c['short_name']:
        c['short_name'] = name
    if contact:
        c['contact'] = text(contact)
    if terms:
        c['settle_type'] = settlement(terms)
    return c


def settlement(terms):
    return ('MONTHLY' if '月结' in str(terms) else
            'PREPAID' if '先款' in str(terms) else 'COD')


def supplier(name, brand=None):
    if name not in suppliers:
        suppliers[name] = add('supplier', name=name, brand=brand)
    return suppliers[name]


purchase_rows = [(i, r) for i, r in rows(workbook['采购订单'], 3) if r[6]]
catalog = defaultdict(set)
for i, r in purchase_rows:
    if '取消' not in str(r[12:]):
        catalog[text(r[6])].add((text(r[2]), text(r[5]), text(r[7])))


def product(part, brand=None, package=None, vendor=None, cost=None):
    part = text(part)
    candidates = catalog.get(part, set())
    if len(candidates) == 1 and vendor is None:
        vendor, known_brand, known_package = next(iter(candidates))
        brand = brand or known_brand
        package = package or known_package
    vendor = vendor or '待核实供应商（Excel未提供）'
    brand = brand or '待核实品牌'
    key = (vendor, brand, part, package)
    if key not in products:
        products[key] = add('product', supplier_id=supplier(vendor, brand)['id'],
                            brand=brand, part_no=part, package_type=package,
                            base_price=cost, tax_rate=None)
    elif cost is not None:
        products[key]['base_price'] = cost
    return products[key]


purchase_groups = defaultdict(list)
for i, r in purchase_rows:
    if '取消' in str(r[12:]):
        note('采购订单', i, '已取消的明细不导入；原始内容见留档。')
        continue
    purchase_groups[(r[3], r[2])].append((i, r))

for (number, vendor), group in purchase_groups.items():
    po = add('purchase_order', purchase_order_no=number, order_time=group[0][1][0],
             supplier_id=supplier(vendor, group[0][1][5])['id'], supplier_name=vendor,
             delivery_time=None, order_status='PENDING_RECEIPT', payment_status='UNPAID',
             remark=f"Excel采购订单 行:{','.join(str(i) for i, _ in group)}；采购人:{group[0][1][1]}；结算:{group[0][1][4]}；预计交期原文:{group[0][1][11] or '未提供'}")
    item_groups = defaultdict(list)
    for i, r in group:
        item_groups[(r[6], r[7], str(r[9]))].append((i, r))
    received_total = ordered_total = Decimal(0)
    paid_total = Decimal(0)
    for (part, package, unit), events in item_groups.items():
        p = product(part, events[0][1][5], package, vendor, money(unit))
        received = sum(int(r[13] or 0) for _, r in events if isinstance(r[12], datetime))
        remaining = next((int(r[14]) for _, r in reversed(events) if isinstance(r[14], (int, float))), None)
        original = max(int(r[8] or 0) for _, r in events)
        quantity = max(original, received)
        if remaining is not None and received + remaining != quantity:
            note('采购订单', events[0][0], f'{number}/{part} 历史最大数量{original}，累计入库{received}、末次余额{remaining}；导入数量{quantity}，余额存在冲突，不据此推测追加采购。')
        if Decimal(unit) != money(unit):
            note('采购订单', events[0][0], f'{part} 原单价{unit}，decimal(12,2)只能保存为{money(unit)}；金额保留原精度计算值，单价×数量将不等于金额。')
        item = add('purchase_order_item', purchase_order_id=po['id'], purchase_order_no=number,
                   product_id=p['id'], brand=p['brand'], part_no=part, package_type=package,
                   unit_price=money(unit), tax_included=1, tax_rate=None, quantity=quantity,
                   amount=money(Decimal(unit) * quantity))
        ordered_total += item['amount']
        received_total += received
        for i, r in events:
            TRACE.append(dict(sheet='采购订单', row=i, table='purchase_order_item', id=item['id']))
            if not isinstance(r[12], datetime) or not r[13]:
                continue
            inbound = add('inbound_order', purchase_order_id=po['id'], purchase_order_no=number,
                          supplier_id=po['supplier_id'], supplier_name=vendor,
                          inbound_type='PURCHASE', status='COMPLETED', inbound_time=r[12])
            add('inbound_order_item', inbound_order_id=inbound['id'], purchase_order_id=po['id'],
                purchase_order_no=number, purchase_order_item_id=item['id'], inbound_quantity=int(r[13]))
            if r[16] == '是':
                paid_total += money(Decimal(unit) * int(r[13]))
            if r[15] is not None and r[16] != '是':
                note('采购订单', i, '付款金额栏有值但未明确已付款，未作为已付金额。')
        if remaining is not None and received + remaining != quantity:
            note('采购订单', events[-1][0], f'{number}/{part} 入库合计+末次余额与初始采购量不符，原始记录保留。')
    total_quantity = sum(x['quantity'] for x in TABLES['purchase_order_item'] if x['purchase_order_id'] == po['id'])
    po['order_status'] = 'COMPLETED' if received_total >= total_quantity else 'PARTIALLY_RECEIVED' if received_total else 'PENDING_RECEIPT'
    po['payment_status'] = 'PAID' if paid_total >= ordered_total else 'PARTIALLY_PAID' if paid_total else 'UNPAID'

sales = {}
sales_items = {}
for i, r in rows(workbook['销售订单'], 3):
    if not r[5]:
        continue
    c = customer(r[1], r[2])
    if '取消' in str(r[12]):
        note('销售订单', i, '取消订单不导入；ET2811/ZNZPO 与出库表 ET8211/ZXZPO 不一致，不自动更正。')
        continue
    key = (c['id'], r[11], r[0])
    if key not in sales:
        sales[key] = add('sales_order', order_no=f'XL-SO-{i:04}', customer_order_no=r[11],
                         order_time=r[0], customer_id=c['id'], customer_name=c['name'],
                         settle_type=settlement(r[2]), delivery_time=None,
                         order_status='PENDING_SHIPMENT', payment_status='UNPAID',
                         remark=f'Excel销售订单 行{i}；账期:{r[2]}；备注:{r[12] or ""}；交期原文:{r[10] or ""}')
    order = sales[key]
    p = product(r[5], r[4], r[6])
    unit, tax = price(r[8])
    amount = money(unit * int(r[7]))
    if r[9] is not None and money(r[9]) != amount:
        note('销售订单', i, f'原金额{r[9]}与数量×单价不符，导入金额{amount}。')
    item = add('sales_order_item', sales_order_id=order['id'], customer_order_no=r[11],
               product_id=p['id'], brand=p['brand'], part_no=r[5], package_type=r[6],
               unit_price=unit, tax_included=tax, tax_rate=None, quantity=int(r[7]), amount=amount)
    sales_items[item['id']] = (order, item)
    TRACE.append(dict(sheet='销售订单', row=i, table='sales_order_item', id=item['id']))

shipped, collected = defaultdict(int), defaultdict(Decimal)
for i, r in rows(workbook['出库登记'], 3):
    if not r[3] or not r[6]:
        continue
    if not isinstance(r[10], datetime):
        note('出库登记', i, '有出库数量但无明确出货日期，暂不导入出库；不继承普通空白日期。')
        continue
    c = customer(r[1]) if r[1] else None
    candidates = [(o, it) for o, it in sales_items.values()
                  if c and o['customer_id'] == c['id'] and o['customer_order_no'] == r[2]
                  and it['part_no'] == r[3] and (r[2] != '无' or o['order_time'] == r[0])]
    if len(candidates) != 1:
        note('出库登记', i, '客户/订单号/型号无法唯一关联有效销售明细，暂不导入。')
        continue
    order, item = candidates[0]
    if shipped[item['id']] + int(r[6]) > item['quantity']:
        note('出库登记', i, '累计出库超过订单数量，暂不导入。')
        continue
    outbound = add('outbound_order', sales_order_id=order['id'], customer_order_no=r[2],
                   customer_id=c['id'], customer_name=c['name'], outbound_type='SALE',
                   status='COMPLETED', outbound_time=r[10])
    detail = add('outbound_order_item', outbound_order_id=outbound['id'], sales_order_id=order['id'],
                 customer_order_no=r[2], sales_order_item_id=item['id'], outbound_quantity=int(r[6]))
    shipped[item['id']] += int(r[6])
    if isinstance(r[7], (int, float)) and item['quantity'] - shipped[item['id']] != int(r[7]):
        note('出库登记', i, f"{r[2]}/{r[3]} 原未交余额{r[7]}与已确认出库推算余额{item['quantity'] - shipped[item['id']]}不符；可能涉及缺日期或重复记录，订单状态仅按已导入出库推算。")
    if r[13] == '是':
        collected[order['id']] += money(price(r[8])[0] * int(r[6]))
    TRACE.append(dict(sheet='出库登记', row=i, table='outbound_order_item', id=detail['id']))

for order in TABLES['sales_order']:
    items = [it for o, it in sales_items.values() if o['id'] == order['id']]
    total = sum(it['quantity'] for it in items)
    actual = sum(shipped[it['id']] for it in items)
    order['order_status'] = 'COMPLETED' if actual == total else 'PARTIALLY_SHIPPED' if actual else 'PENDING_SHIPMENT'
    amount = sum(it['amount'] for it in items)
    order['payment_status'] = 'PAID' if collected[order['id']] >= amount else 'PARTIALLY_PAID' if collected[order['id']] else 'UNPAID'

for i, r in rows(workbook['客户跟进表'], 3):
    if not r[0]:
        continue
    customer(r[0], '次月结60' if '月结' in r[0] else None, r[1])
    note('客户跟进表', i, '缺少可确认年份的拜访日期，未生成customer_visit；报价/需求/拜访内容完整保存在原始留档。')
    if r[5]:
        for part in str(r[5]).splitlines():
            product(part, r[7], r[6])

for i, r in rows(workbook['申请原厂样品表'], 3):
    if not r[1]:
        continue
    p = product(r[1])
    if not r[3]:
        note('申请原厂样品表', i, '未提供客户，属于样品备货记录，暂不写入必填customer_id的sample_apply。')
        continue
    c = customer(r[3])
    sample = add('sample_apply', customer_id=c['id'], apply_time=r[0], project=r[4],
                 product_id=p['id'], quantity=int(r[2]), is_delivered=1 if r[5] == '已送样' else 0)
    TRACE.append(dict(sheet='申请原厂样品表', row=i, table='sample_apply', id=sample['id']))
    if r[5] == '已送样' and r[6]:
        note('申请原厂样品表', i, '已送样但库存栏非0，送样状态按原文保留；样品库存不合并到商品库存。')

for i, r in rows(workbook['库存表'], 2):
    p = product(r[1], r[0])
    assert p['base_price'] is not None, 'Snapshot cost needs review'
    add('inventory', product_id=p['id'], batch_no=f'XL-SNAPSHOT-20261014-{i:03}',
        inbound_order_item_id=None, unit_cost=p['base_price'], initial_quantity=int(r[2]),
        quantity=int(r[2]), reserved_quantity=0, created_at=r[3])


def sql(value):
    if value is None:
        return 'NULL'
    if isinstance(value, datetime):
        value = value.strftime('%Y-%m-%d %H:%M:%S')
    if isinstance(value, str):
        return "'" + value.replace("'", "''") + "'"
    return str(value)


schema = (ROOT / 'glw.sql').read_text(encoding='utf-8')
definitions = dict(re.findall(r'CREATE TABLE `(\w+)`\s*\((.*?)\) ENGINE', schema, re.S))
for table, records in TABLES.items():
    columns = dict(re.findall(r'^\s*`(\w+)`\s+([^\n]+)', definitions[table], re.M))
    for record in records:
        assert not set(record) - set(columns), (table, record)
        for name, definition in columns.items():
            if 'NOT NULL' in definition and 'DEFAULT' not in definition and 'AUTO_INCREMENT' not in definition:
                assert record.get(name) is not None, (table, name)
        for name, value in record.items():
            limit = re.search(r'varchar\((\d+)\)', columns[name])
            if limit and value is not None:
                assert len(value) <= int(limit[1]), (table, name, value)
            if isinstance(value, Decimal):
                assert abs(value) < Decimal('10000000000'), (table, name, value)

references = {'customer_id': 'customer', 'supplier_id': 'supplier', 'product_id': 'product',
              'purchase_order_id': 'purchase_order', 'purchase_order_item_id': 'purchase_order_item',
              'inbound_order_id': 'inbound_order', 'sales_order_id': 'sales_order',
              'sales_order_item_id': 'sales_order_item', 'outbound_order_id': 'outbound_order'}
for table, records in TABLES.items():
    for record in records:
        for column, target in references.items():
            if record.get(column) is not None:
                assert any(x['id'] == record[column] for x in TABLES[target]), (table, column)

order = ['supplier', 'customer', 'product', 'purchase_order', 'purchase_order_item',
         'inbound_order', 'inbound_order_item', 'sales_order', 'sales_order_item',
         'outbound_order', 'outbound_order_item', 'inventory', 'sample_apply']
lines = ['-- Source: 表格.xlsx. Read excel_import_notes.md before execution.',
         '-- MySQL 8; select the target database first. Empty business tables only.',
         '-- Requires CREATE ROUTINE permission. No DROP/TRUNCATE of business tables.',
         'SET NAMES utf8mb4;', 'SET @xl_old_sql_mode = @@SESSION.sql_mode;',
         "SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_BACKSLASH_ESCAPES,NO_ENGINE_SUBSTITUTION';",
         'SET @xl_old_time_zone = @@SESSION.time_zone;', "SET SESSION time_zone = '+08:00';",
         'DELIMITER $$', 'CREATE PROCEDURE `glw_import_excel_20261015`()', 'BEGIN',
         '  DECLARE EXIT HANDLER FOR SQLEXCEPTION', '  BEGIN', '    ROLLBACK;',
         '    SET SESSION sql_mode = @xl_old_sql_mode;',
         '    SET SESSION time_zone = @xl_old_time_zone;', '    RESIGNAL;', '  END;',
         '  START TRANSACTION;']
for table in definitions:
    lines += [f'  IF EXISTS (SELECT 1 FROM `{table}` LIMIT 1) THEN',
              f"    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: {table}';", '  END IF;']
for table in order:
    records = TABLES.get(table, [])
    if not records:
        continue
    columns = list(records[0])
    lines += [f'  -- {table}: {len(records)} rows',
              f'  INSERT INTO `{table}` (' + ', '.join(f'`{c}`' for c in columns) + ') VALUES',
              ',\n'.join('    (' + ', '.join(sql(r[c]) for c in columns) + ')' for r in records) + ';']
lines += ['  COMMIT;', 'END$$', 'DELIMITER ;', 'CALL `glw_import_excel_20261015`();',
          'DROP PROCEDURE `glw_import_excel_20261015`;',
          'SET SESSION sql_mode = @xl_old_sql_mode;',
          'SET SESSION time_zone = @xl_old_time_zone;']
for table in order:
    lines.append(f"SELECT '{table}' AS table_name, COUNT(*) AS actual_rows, {len(TABLES.get(table, []))} AS expected_rows FROM `{table}`;")
(ROOT / 'excel_import.sql').write_text('\n'.join(lines) + '\n', encoding='utf-8')
(ROOT / 'excel_import_source.json').write_text(json.dumps(dict(sheets=raw, trace=TRACE),
    ensure_ascii=False, default=str, indent=2) + '\n', encoding='utf-8')
notes = '''# Excel 数据整理与导入说明

## 执行

适用于 MySQL 8，先在空库执行 glw.sql 建表，再选择该库执行 excel_import.sql。
原 glw.sql 含 DROP TABLE，只能在确认可重建的空库执行。导入脚本不删除业务数据。
脚本使用 DELIMITER 和存储过程，Navicat / MySQL CLI 需以脚本方式执行；需要 CREATE ROUTINE 权限。
不要在已有事务中执行。任意业务表非空将拒绝导入；插入异常将回滚全部数据。
执行成功后再次运行会拒绝，避免重复导入。未实际连接 MySQL 执行。
如执行失败且客户端中止，过程可能保留；核实错误后删除 glw_import_excel_20261015 过程再重试。

## 整理规则与限制

- 只填充Excel有依据的字段，税率、电话、地址和操作人不推测。
- 合并单元格还原；普通空白不默认继承，尤其不继承出货日期。
- 客户简称按本文生成器中的明确别名表合并；名称映射仍应业务复核。
- 采购按供应商+订单号合并，明细按型号+封装+原单价合并，滚动余额不重复当采购量。
  数量取历史最大数量与累计收货量的较大者，末次未交余额冲突逐条列出，不据此推测追加采购。
- 已取消明细不导入。取消销售订单与出库表的型号及单号冲突保持待核对。
- 内部销售单号生成XL-SO-前缀，客户采购单号保留原值，包括原文“无”。
- 销售金额按数量×单价纠正；采购0.075按表结构四舍五入为0.08，金额仍按原0.075计算。
  该精度损失会影响后续成本核算，正式使用前建议确认是否将相关单价列统一改为更多小数位。
- 采购付款按明确标记“是”的入库数量×原价汇总，付款金额栏不直接视为已付款。
  销售收款按明确标记“是”的有效出库记录汇总；订单金额未全付时为PARTIALLY_PAID。
  原表没有资金流水表对应结构，付款/收款日期和原金额等在JSON留档中保留。
- 客户跟进表缺少确定的拜访日期，全部留待补齐，不编造visit_time和缺失报价。
  客户和推荐产品主数据已提取；客户/供应商无依据的必填品牌或供应商使用明确“待核实”占位。
- 样品无客户的记录暂不导入sample_apply；样品库存不计入商品库存。
- 库存仅导入库存表2026-10-14快照，初始数量设为盘点数量，成本取同产品采购价。
  快照并非历史采购批次，inbound_order_item_id为空，reserved_quantity=0。
  没有可靠历史批次与预占依据，sales_order_batch_item/outbound_order_batch_item保持空表。
  历史出入库不与当前快照重复累加，无法据此回溯批次成本或确认全部未交订单的可用库存。
- 原始各sheet非空行完整保存于excel_import_source.json，trace提供采购明细、销售明细、出库明细、样品申请的源行对应。

## 导入数量

| 表 | 条数 |
| --- | ---: |
'''
notes += '\n'.join(f'| {t} | {len(TABLES.get(t, []))} |' for t in definitions)
notes += '\n\n## 待核对及未导入记录\n\n' + '\n'.join('- ' + n for n in ISSUES) + '\n'
(ROOT / 'excel_import_notes.md').write_text(notes, encoding='utf-8')
print(json.dumps({t: len(TABLES.get(t, [])) for t in definitions}, ensure_ascii=False, indent=2))
print(f'Review issues: {len(ISSUES)}; schema, required fields, lengths and references checked.')
