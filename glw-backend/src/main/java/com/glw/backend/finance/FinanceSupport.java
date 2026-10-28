package com.glw.backend.finance;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared currency rounding and financial error checks. */
public final class FinanceSupport {
    private FinanceSupport() {}
    public static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    public static BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }

    public static BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }

    public static BusinessException missing() { return new BusinessException(ErrorCode.NOT_FOUND, "财务记录不存在"); }

    public static void changed(int count) { if (count != 1) throw conflict("记录状态或余额已变化，请刷新"); }
}
