package com.gmc.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardStatsResponse {

    private long totalOrders;
    private BigDecimal totalRevenue;
    private long pendingOrders;
    private long appointmentsToday;
    private List<ProductResponse> lowStockProducts;
    private long totalUsers;
    private long totalProducts;
}
