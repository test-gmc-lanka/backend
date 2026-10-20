package com.gmc.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gmc.backend.dto.request.MaintenanceAssignRequest;
import com.gmc.backend.dto.response.MaintenanceResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.model.Maintenance;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.Role;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.MaintenanceRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private MaintenanceServiceImpl maintenanceService;

    private static Role technicianRole() {
        return Role.builder().roleId(3L).roleName("TECHNICIAN").build();
    }

    private User technician() {
        return User.builder().userId(7L).name("Tech").role(technicianRole()).build();
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
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(
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
        request.setImageUrls(Arrays.asList(
                "/uploads/maintenance/a.jpg", null, " ", "/uploads/maintenance/a.jpg", "/uploads/maintenance/b.jpg"));

        MaintenanceResponse result = maintenanceService.assignMaintenance(request);

        assertThat(result.getProductId()).isNull();
        assertThat(result.getTitle()).isEqualTo("Carved door panel");
        assertThat(result.getImageUrls()).containsExactly("/uploads/maintenance/a.jpg", "/uploads/maintenance/b.jpg");
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
                .maintenanceId(1L)
                .description("d")
                .status("ASSIGNED")
                .cost(BigDecimal.ONE)
                .user(technician())
                .title("Panel")
                .imageUrls(List.of("/uploads/maintenance/a.jpg"))
                .build();
        when(maintenanceRepository.findByUser_UserId(7L)).thenReturn(List.of(task));

        List<MaintenanceResponse> result = maintenanceService.getMaintenanceByTechnician(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Panel");
        assertThat(result.get(0).getImageUrls()).containsExactly("/uploads/maintenance/a.jpg");
    }

    @Test
    void assign_notifiesTechnicianInAppAndByEmail() {
        User tech = User.builder()
                .userId(7L)
                .name("Tech")
                .email("tech@example.com")
                .role(technicianRole())
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(tech));
        when(maintenanceRepository.save(any(Maintenance.class))).thenAnswer(inv -> {
            Maintenance m = inv.getArgument(0);
            m.setMaintenanceId(4L);
            return m;
        });
        MaintenanceAssignRequest request = request();
        request.setTitle("Carved panel");

        maintenanceService.assignMaintenance(request);

        verify(notificationService)
                .sendNotification(eq(7L), contains("New task #4"), eq("MAINTENANCE"), eq("/technician/tasks"));
        verify(emailService).send(eq("tech@example.com"), contains("#4"), contains("Carved panel"));
    }

    @Test
    void updateStatus_byAssignedTechnician_completesAndNotifiesAdmins() {
        Maintenance task = Maintenance.builder()
                .maintenanceId(4L)
                .description("d")
                .status("IN_PROGRESS")
                .cost(BigDecimal.ONE)
                .user(technician())
                .title("Panel")
                .build();
        when(maintenanceRepository.findById(4L)).thenReturn(Optional.of(task));
        when(maintenanceRepository.save(any(Maintenance.class))).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceResponse result = maintenanceService.updateMaintenanceStatus(4L, 7L, "completed");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(notificationService)
                .notifyAdmins(
                        contains("completed maintenance task #4"),
                        eq("MAINTENANCE"),
                        eq("/admin/maintenance"),
                        isNull());
    }

    @Test
    void updateStatus_byAnotherTechnician_isRejected() {
        Maintenance task = Maintenance.builder()
                .maintenanceId(4L)
                .status("ASSIGNED")
                .user(technician())
                .build();
        when(maintenanceRepository.findById(4L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> maintenanceService.updateMaintenanceStatus(4L, 99L, "COMPLETED"))
                .isInstanceOf(BusinessRuleException.class);
        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void updateStatus_invalidStatus_isRejected() {
        Maintenance task = Maintenance.builder()
                .maintenanceId(4L)
                .status("ASSIGNED")
                .user(technician())
                .build();
        when(maintenanceRepository.findById(4L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> maintenanceService.updateMaintenanceStatus(4L, 7L, "DONE"))
                .isInstanceOf(BusinessRuleException.class);
        verify(notificationService, never()).notifyAdmins(anyString(), anyString(), anyString(), any());
    }

    @Test
    void assign_storesDeadlineAndIncludesItInTechnicianNotice() {
        User tech = User.builder()
                .userId(7L)
                .name("Tech")
                .email("tech@example.com")
                .role(technicianRole())
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(tech));
        when(maintenanceRepository.save(any(Maintenance.class))).thenAnswer(inv -> {
            Maintenance m = inv.getArgument(0);
            m.setMaintenanceId(4L);
            return m;
        });
        MaintenanceAssignRequest request = request();
        request.setTitle("Carved panel");
        request.setDeadline(LocalDate.of(2099, 1, 15));

        MaintenanceResponse result = maintenanceService.assignMaintenance(request);

        assertThat(result.getDeadline()).isEqualTo(LocalDate.of(2099, 1, 15));
        verify(notificationService)
                .sendNotification(eq(7L), contains("Due Thu, 15 Jan 2099"), eq("MAINTENANCE"), eq("/technician/tasks"));
        verify(emailService).send(eq("tech@example.com"), anyString(), contains("Deadline: Thu, 15 Jan 2099"));
    }

    @Test
    void assign_rejectsDeadlineInThePast() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(technician()));
        MaintenanceAssignRequest request = request();
        request.setTitle("Carved panel");
        request.setDeadline(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> maintenanceService.assignMaintenance(request))
                .isInstanceOf(BusinessRuleException.class);
        verify(maintenanceRepository, never()).save(any());
    }

    @Test
    void assign_toNonTechnician_isRejected() {
        User customer = User.builder()
                .userId(7L)
                .name("Cust")
                .role(Role.builder().roleId(1L).roleName("CUSTOMER").build())
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        MaintenanceAssignRequest request = request();
        request.setTitle("Panel");

        assertThatThrownBy(() -> maintenanceService.assignMaintenance(request))
                .isInstanceOf(BusinessRuleException.class);
        verify(maintenanceRepository, never()).save(any());
    }
}
