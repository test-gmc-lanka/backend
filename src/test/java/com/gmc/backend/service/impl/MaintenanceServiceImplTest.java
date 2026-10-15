package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.MaintenanceAssignRequest;
import com.gmc.backend.dto.response.MaintenanceResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.model.Maintenance;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.MaintenanceRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MaintenanceServiceImpl}: product tasks, custom design tasks and images.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceServiceImplTest {

    @Mock
    private MaintenanceRepository maintenanceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private MaintenanceServiceImpl maintenanceService;

    private User technician() {
        return User.builder().userId(7L).name("Tech").build();
    }

    private MaintenanceAssignRequest request() {
        MaintenanceAssignRequest request = new MaintenanceAssignRequest();
        request.setDescription("Fix it");
        request.setTechnicianId(7L);
        request.setCost(new BigDecimal("1500"));
        return request;
    }

    @Test
    void assign_withProduct_linksProduct() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician()));
        when(productRepository.findById(3L)).thenReturn(Optional.of(
                Product.builder().productId(3L).name("Chair").build()));
        when(maintenanceRepository.save(any(Maintenance.class))).thenAnswer(inv -> inv.getArgument(0));
        MaintenanceAssignRequest request = request();
        request.setProductId(3L);

        MaintenanceResponse result = maintenanceService.assignMaintenance(request);

        assertThat(result.getProductName()).isEqualTo("Chair");
        assertThat(result.getTitle()).isNull();
        assertThat(result.getStatus()).isEqualTo("ASSIGNED");
    }

    @Test
    void assign_customDesign_storesTitleAndImages() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician()));
        when(maintenanceRepository.save(any(Maintenance.class))).thenAnswer(inv -> inv.getArgument(0));
        MaintenanceAssignRequest request = request();
        request.setTitle("  Carved door panel ");
        request.setImageUrls(Arrays.asList("/uploads/maintenance/a.jpg", null, " ",
                "/uploads/maintenance/a.jpg", "/uploads/maintenance/b.jpg"));

        MaintenanceResponse result = maintenanceService.assignMaintenance(request);

        assertThat(result.getProductId()).isNull();
        assertThat(result.getTitle()).isEqualTo("Carved door panel");
        assertThat(result.getImageUrls())
                .containsExactly("/uploads/maintenance/a.jpg", "/uploads/maintenance/b.jpg");
        verifyNoInteractions(productRepository);
    }

    @Test
    void assign_withoutProductOrTitle_throws() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician()));
        MaintenanceAssignRequest request = request();
        request.setTitle("   ");

        assertThatThrownBy(() -> maintenanceService.assignMaintenance(request))
                .isInstanceOf(BusinessRuleException.class);
        verify(maintenanceRepository, never()).save(any(Maintenance.class));
    }

    @Test
    void getMaintenanceByTechnician_mapsCustomDesignWithoutProduct() {
        Maintenance task = Maintenance.builder()
                .maintenanceId(1L).description("d").status("ASSIGNED")
                .cost(BigDecimal.ONE).user(technician()).title("Panel")
                .imageUrls(List.of("/uploads/maintenance/a.jpg"))
                .build();
        when(maintenanceRepository.findByUser_UserId(7L)).thenReturn(List.of(task));

        List<MaintenanceResponse> result = maintenanceService.getMaintenanceByTechnician(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Panel");
        assertThat(result.get(0).getImageUrls()).containsExactly("/uploads/maintenance/a.jpg");
    }
}
