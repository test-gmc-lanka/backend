package com.gmc.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

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
