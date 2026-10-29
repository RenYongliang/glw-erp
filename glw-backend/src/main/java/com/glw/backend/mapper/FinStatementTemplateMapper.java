package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.finance.FinanceModels.StatementTemplate;
import com.glw.backend.model.entity.FinStatementTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FinStatementTemplateMapper extends BaseMapper<FinStatementTemplate> {
    /** 优先取往来单位自己的模板，其次取全局默认模板。 */
    FinStatementTemplate resolve(@Param("docType") String docType,
                                 @Param("direction") String direction, @Param("partnerId") Long partnerId,
                                 @Param("format") String format);

    /** 未指定格式时优先取往来单位的默认模板，没有默认时按 XLSX -> PDF 的顺序兜底。 */
    FinStatementTemplate resolveAny(@Param("docType") String docType,
                                    @Param("direction") String direction, @Param("partnerId") Long partnerId);

    FinStatementTemplate template(@Param("id") Long id);

    FinStatementTemplate templateVersion(@Param("id") Long id, @Param("version") Integer version);

    List<StatementTemplate> templates(@Param("docType") String docType,
                                      @Param("direction") String direction,
                                      @Param("partnerId") Long partnerId);

    int nextVersion(@Param("docType") String docType, @Param("direction") String direction,
                    @Param("partnerId") Long partnerId, @Param("format") String format);

    /** 清掉该往来单位所有格式的默认标记，保证出单时只有一个默认模板。 */
    int clearDefault(@Param("docType") String docType, @Param("direction") String direction,
                     @Param("partnerId") Long partnerId, @Param("format") String format);

    String layout(@Param("id") Long id);
}
