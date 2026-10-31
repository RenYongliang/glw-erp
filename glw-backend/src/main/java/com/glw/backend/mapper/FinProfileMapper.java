package com.glw.backend.mapper;

import com.glw.backend.finance.FinanceModels.Profile;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.FinProfile;
import com.glw.backend.finance.FinanceModels.Partner;
import com.glw.backend.finance.FinanceModels.PartnerInfo;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FinProfileMapper extends BaseMapper<FinProfile> {
    List<Profile> profiles(String direction);
    Profile profile(@Param("direction") String direction, @Param("partnerId") Long partnerId);
    /** 公司默认结算方案：partner_id = 0 的档案行。 */
    Profile companyProfile(@Param("direction") String direction);
    /** 已开启自动出单的结算档案，供定时任务按方向遍历；公司默认行不参与出单。 */
    List<Profile> autoProfiles(@Param("direction") String direction);
    /** 沿用公司默认方案的客户，默认行保存后批量同步。 */
    List<Long> defaultPartnerIds(@Param("direction") String direction);
    /** 把公司默认行的方案字段同步到所有 DEFAULT 客户行，不改各自的自动出单开关。 */
    int syncDefault(@Param("direction") String direction);
    List<String> autoProfileDirections();
    void saveProfile(Profile profile);
    List<Partner> partners(String direction);
    PartnerInfo partnerInfo(@Param("direction") String direction, @Param("partnerId") Long partnerId);
}
