package com.glw.backend.finance.statement;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 模板中心支持的单据类型。对账单区分收/付方向，合同与送货单由业务类型决定方向。 */
public enum DocumentType {
    STATEMENT("对账单", true, null),
    SALES_CONTRACT("销售合同", false, "AR"),
    PURCHASE_CONTRACT("采购合同", false, "AP"),
    DELIVERY_NOTE("送货单", false, "AR");

    private final String label;
    private final boolean directional;
    private final String fixedDirection;

    DocumentType(String label, boolean directional, String fixedDirection) {
        this.label = label;
        this.directional = directional;
        this.fixedDirection = fixedDirection;
    }

    public String label() {
        return label;
    }

    public boolean directional() {
        return directional;
    }

    /** 对账单按往来方向显示不同标题，其余单据直接用类型名称。 */
    public String title(String direction) {
        if (this != STATEMENT) return label;
        return "AP".equals(direction) ? "对账单（应付）" : "对账单（应收）";
    }

    public String normalizeDirection(String raw) {
        String direction = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (directional) {
            if (!Set.of("AR", "AP").contains(direction))
                throw bad("方向必须为 AR 或 AP");
            return direction;
        }
        if (direction.isBlank()) return fixedDirection;
        if (!fixedDirection.equals(direction))
            throw bad(label + "的往来方向必须为 " + fixedDirection);
        return fixedDirection;
    }

    public static DocumentType of(String raw) {
        String code = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (code.isBlank()) return STATEMENT;
        for (DocumentType type : values()) {
            if (type.name().equals(code)) return type;
        }
        throw bad("不支持的单据类型：" + raw);
    }

    /** 供前端渲染类型切换。 */
    public static List<Option> options() {
        return List.of(values()).stream()
                .map(type -> new Option(type.name(), type.label, type.directional,
                        type.directional ? List.of("AR", "AP") : List.of(type.fixedDirection)))
                .toList();
    }

    private static BusinessException bad(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    public record Option(String code, String label, boolean directional, List<String> directions) {
    }
}
