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
    void saveProfile(Profile profile);
    List<Partner> partners(String direction);
    PartnerInfo partnerInfo(@Param("direction") String direction, @Param("partnerId") Long partnerId);
}
