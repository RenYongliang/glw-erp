# 业务表持久层

以 `src/main/resources/db/glw.sql`（提交 `c8f199a`）的 14 张业务表为准：
customer、customer_visit、inbound_order、inbound_order_item、inventory、outbound_order、outbound_order_item、product、purchase_order、purchase_order_item、sales_order、sales_order_item、sample_apply、supplier。

每张表对应 `model/entity` 实体、`mapper` 接口、`resources/mapper` XML、`service` 接口和 `service/impl` 实现。

- Mapper 继承 `BaseMapper<T>`，Service 继承 `IService<T>`，实现继承 `ServiceImpl`。可直接使用 save、getById、list、page、updateById、removeById 等基础操作。
- XML 提供完整的 `BaseResultMap` 和 `Base_Column_List`，实体明确引用对应 resultMap；通用 CRUD SQL 由 MyBatis-Plus 注入，不重复手写。需要联表查询时，在对应 Mapper 和 XML 中扩展。
- bigint → Long；普通 int → Integer；decimal → BigDecimal；timestamp → LocalDateTime；varchar/text → String。字段注释和命名与 DDL 对应，包含最新的 purchase_order_item_id。
- id 由数据库自增。created_at、updated_at 由 MySQL 默认值和 ON UPDATE 维护，插入和更新时均不回写这两个字段；更新后如需最新时间请重新查询。
- created_by、updated_by 暂由调用业务显式设置。没有臆造自动填充的用户来源。
- 对有数据库默认值的状态/标志字段，新增对象保持 null 时会省略该列，由数据库默认值生效。入库、出库 status 在最新 DDL 中没有默认值，调用方必须赋值。
- 这些业务表没有 deleted 字段，因此不加逻辑删除注解，removeById 是物理删除。
- Mapper 继续由现有非 mock 模式 MapperScan 注册，Service 实现也标记 @Profile("!mock")；mock 模式不会要求数据源。

这次提供持久层基础代码，未自动增加业务 Controller、出入库扣减/恢复、状态机或主从表级联操作。这些功能应在独立业务接口中结合校验、权限、事务和并发控制实现，不能仅靠单表 CRUD。

验证：`./mvnw.cmd verify`。BusinessMapperTest 加载真实 XML，逐表对照 DDL 校验类型、结果映射以及生成的 CRUD SQL；现有 mock 登录测试也会运行。测试不连接或修改真实 MySQL，业务 SQL 文件没有自动执行。

实体使用 Lombok `@Data` 生成 getter/setter、equals/hashCode 和 toString。Lombok 版本由 Spring Boot 管理，Maven 显式配置注解处理器，打包时排除 Lombok。SysUser 的 passwordHash 使用 @ToString.Exclude；Account、UserView 保留 record 形式。

## 数据库枚举

`model/enums` 定义 11 个枚举，应用于 8 个实体的 13 个字段：TaxIncluded（拜访报价、采购/销售商品是否含税）、SampleDeliveryStatus（送样标志）、InboundType/InboundStatus、OutboundType/OutboundStatus、PurchaseOrderStatus/PurchasePaymentStatus、SalesOrderStatus/SalesPaymentStatus、SettlementType（销售结算类型）。

每个枚举提供 code、description、fromCode。@EnumValue 使 MyBatis-Plus 读写数据库原有编码，@JsonValue / @JsonCreator 保持 JSON 使用原有字符串或数字，非法 JSON 编码会报参数错误；不使用 ordinal。XML 明确标注对应的 javaType，原 jdbcType 不变。数据库默认值仍由 MySQL 处理，不在实体中硬编码初始状态。

客户 customer.settle_type、拜访进度等字段在 DDL 中没有限定完整取值，仍为 String。SysUser.enabled 和 deleted 保留原有布尔及逻辑删除映射。

## 库存预占字段

产品表只维护供应商、品牌、型号、封装等资料，不维护基础价格或税率。
采购合同约定的 `unit_price`、`tax_included`、`tax_rate` 保留在 `purchase_order_item`，未入库时也能记录合同价格。
`inbound_order_item` 通过采购明细关联价格，不重复存储价格及税务字段。
`inventory.unit_cost` 记录批次单位成本，`outbound_order_batch_item.unit_cost` 保留实际出库时的成本快照。
当前初始化数据的采购明细均为含税价，库存单位成本直接沿用对应合同含税单价，不进行税额剥离或运费分摊；税率未明确时保留NULL。

批次号只存储在 `inventory.batch_no`，不在入库明细或出库批次明细中重复存储。
一条产品入库明细形成一个批次，`inventory.inbound_order_item_id` 指向该明细；入库详情通过该关联查询批次号。
同一入库单中的不同产品有各自的批次；出库经 `outbound_order_batch_item.inventory_id` 关联实际消耗批次。
编号为 `BAT` + 实际入库日期yyyyMMdd + 当天全局三位流水号，例如 `BAT20261016001`，不按产品重置流水。
初始化脚本中同日按入库明细ID递增编号，保持现有库存ID及全部出库批次关联不变。
`inventory.batch_no` 保留唯一索引，批次号创建后不修改；耗尽批次保留记录、数量为0，不物理删除。
线上入库流程尚未接入编号生成，后续需使用数据库事务或原子计数器分配当天流水，不能直接用MAX+1；超过999时明确报错，不循环复用编号。
数据导入按实际入库日期、入库明细ID进行FIFO分配，仅消耗出库当日及之前的入库，不使用未来批次。
这是导入数据的分配规则，当前基础Service仍不自动执行线上FIFO扣减。

已确认业务规则：销售下单不预占、不根据库存限制下单，未交订单可以超过库存，随后再安排采购。
`inventory.reserved_quantity` 暂保留兼容，初始化全部为0；`sales_order_batch_item` 为预占关系表，本次不生成记录。
实际已出库批次只记录在 `outbound_order_batch_item`，不将其冒充预占关系。

实体不初始化为 0：新增时省略该字段由数据库使用默认值；部分更新时未赋值不覆盖已有预占量。Mapper 的 resultMap、列清单及继承的 Mapper/Service CRUD 已支持此字段。

使用最新 `src/main/resources/db/glw.sql` 建表。当前基础Service没有库存校验或预占逻辑；本次导入生成历史FIFO及结余，线上发货扣减逻辑尚未接入。

## 销售取消余量

`sales_order_item.cancelled_quantity` 为 INT NOT NULL DEFAULT 0，对应 `SalesOrderItem.cancelledQuantity`。
`quantity` 和 `amount` 保留原订数量、原订金额，有效数量为 `quantity - cancelled_quantity`，有效应收为有效数量乘单价。
未交量为有效数量减累计实际出库量。业务写入时应校验取消量在0至原订量减已出库量之间。
`SalesOrderStatus.CANCELLED` 表示剩余未交已取消，保留已出库及已收款历史。
初始化脚本据有效应收计算付款状态，中诺泽订单原订200000、已交24000、取消176000，实际应收7680已结清。
本次只补充持久层与导入，线上金额汇总及取消流程尚未实现，后续需使用上述有效数量口径。

## 乐观锁版本号

customer、supplier、product、customer_visit、sample_apply、sales_order、purchase_order、inbound_order、outbound_order、inventory，以及 schema.sql 中的 sys_user，均新增 version INT NOT NULL DEFAULT 0。实体用 Integer version 和 @Version，业务表 XML 同步结果映射及字段清单；SysUser 沿用注解映射。MybatisPlusConfig 启用 OptimisticLockerInnerInterceptor。

实体 version 不初始化，新增时由数据库默认 0；更新时必须携带查询所得版本。支持的 MyBatis-Plus 实体更新会比较旧版本并递增新版本。没有传入 version、仅使用 Wrapper 更新、自定义 SQL 或删除操作不能假定自动受此插件保护。

这是表结构和持久层的准备，完整业务接口仍需校验请求版本非空、检查更新影响行数，并在冲突时返回 409。整单保存必须在同一事务中先更新主表版本，再处理明细与库存，失败则回滚；仅修改明细也必须更新主表版本。四张订单明细表暂不加 version。库存条件更新及出入库状态切换需要显式协调版本号，不能只靠 @Version 保证业务并发安全。

数据库尚未初始化，本次仅修改 glw.sql 和 schema.sql 建表语句，没有增量 SQL，也未执行真实数据库操作。
