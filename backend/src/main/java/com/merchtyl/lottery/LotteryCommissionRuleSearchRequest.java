package com.merchtyl.lottery;

import java.util.UUID;

public record LotteryCommissionRuleSearchRequest(
        UUID operatorId,
        UUID jurisdictionId,
        UUID storeId,
        LotteryCommissionRuleType ruleType,
        LotteryCommissionRuleStatus status,
        Integer page,
        Integer size
) {
}
