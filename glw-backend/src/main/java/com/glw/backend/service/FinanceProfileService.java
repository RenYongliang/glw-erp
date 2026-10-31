package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinProfile;
import com.glw.backend.finance.FinanceModels;
import com.glw.backend.finance.FinanceModels.Partner;
import java.util.List;

/** Partner settlement terms. */
public interface FinanceProfileService extends IService<FinProfile> {
    List<Partner> partners(String direction);
    List<FinanceModels.Profile> profiles(String direction);
    FinanceModels.SaveProfileResult saveProfile(FinanceModels.Profile input);
    Partner partner(String direction, Long id);
}
