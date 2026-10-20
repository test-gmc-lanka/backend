package com.gmc.backend.service.impl;

import static com.gmc.backend.model.AppointmentStatus.APPROVED;
import static com.gmc.backend.model.AppointmentStatus.CANCELLED;
import static com.gmc.backend.model.AppointmentStatus.COMPLETED;
import static com.gmc.backend.model.AppointmentStatus.PENDING;
import static com.gmc.backend.model.AppointmentStatus.RESCHEDULED;

import com.gmc.backend.dto.request.AppointmentRequest;
import com.gmc.backend.dto.request.RescheduleRequest;
import com.gmc.backend.dto.response.AppointmentResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Appointment;
import com.gmc.backend.model.AppointmentStatus;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.model.User;
import com.gmc.backend.repository.AppointmentRepository;
import com.gmc.backend.repository.ProductCategoryRepository;
import com.gmc.backend.repository.UserRepository;
import com.gmc.backend.service.AppointmentService;
import com.gmc.backend.service.EmailService;
import com.gmc.backend.service.NotificationService;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ProductCategoryRepository categoryRepository;

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    // ════════════════════════════ CUSTOMER SIDE ════════════════════════════

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(Long userId, AppointmentRequest request) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        ProductCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository
                    .findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessRuleException("Please choose a valid product line"));
        }
        Appointment appointment = Appointment.builder()
                .date(request.getDate())
                .message(request.getMessage())
                .status(PENDING)
                .user(user)
                .category(category)
                .imageUrls(uploadedImages(request.getImageUrls()))
                .build();
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(
                userId,
                "We received your appointment request for " + format(request.getDate()) + ". We'll confirm it soon.");
        // A customer is waiting for a reply, so admins also get an email
        int images = saved.getImageUrls().size();
        notificationService.notifyAdmins(
                "New appointment request from " + user.getName()
                        + (category != null ? " about " + category.getName() : "") + " for "
                        + format(request.getDate())
                        + (images > 0 ? " (" + images + " image" + (images > 1 ? "s" : "") + " attached)" : "") + ": \""
                        + request.getMessage() + "\"",
                "APPOINTMENT",
                ADMIN_APPOINTMENTS_LINK,
                "New appointment request");
        return toResponse(saved);
    }

    // Customer asks for a new time -> goes back to PENDING so the admin must approve it again
    @Override
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, Long userId, RescheduleRequest request) {
        Appointment appointment = findByIdAndUser(appointmentId, userId);
        requireStatus(appointment, "reschedule", PENDING, APPROVED, RESCHEDULED);
        appointment.setDate(request.getNewDate());
        appointment.setStatus(PENDING);
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(
                userId,
                "Reschedule requested for " + format(request.getNewDate()) + ". We'll confirm the new time soon.");
        notificationService.notifyAdmins(
                appointment.getUser().getName() + " asked to reschedule their " + "appointment to "
                        + format(request.getNewDate()) + ". Please approve the new time.",
                "APPOINTMENT",
                ADMIN_APPOINTMENTS_LINK,
                null);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse cancelAppointment(Long appointmentId, Long userId) {
        Appointment appointment = findByIdAndUser(appointmentId, userId);
        requireStatus(appointment, "cancel", PENDING, APPROVED, RESCHEDULED);
        appointment.setStatus(CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyUser(userId, "Your appointment on " + format(saved.getDate()) + " was cancelled.");
        notificationService.notifyAdmins(
                appointment.getUser().getName() + " cancelled their appointment on " + format(saved.getDate()) + ".",
                "APPOINTMENT",
                ADMIN_APPOINTMENTS_LINK,
                null);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByUser(Long userId) {
        return appointmentRepository.findByUserUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ════════════════════════════ ADMIN SIDE ═══════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllAppointments() {
        return appointmentRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByStatus(AppointmentStatus status) {
        return appointmentRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // APPROVED button
    @Override
    @Transactional
    public AppointmentResponse approveAppointment(Long appointmentId, String meetingLink, String note) {
        Appointment appointment = findById(appointmentId);
        requireStatus(appointment, "approve", PENDING, RESCHEDULED);
        if (appointment.getDate().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException(
                    "This appointment time has already passed. " + "Reschedule it to a new time instead.");
        }
        appointment.setMeetingLink(validateMeetingLink(meetingLink));
        appointment.setAdminNote(clean(note));
        appointment.setStatus(APPROVED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyCustomer(
                saved, "Appointment approved", "Your appointment on " + format(saved.getDate()) + " was approved.");
        return toResponse(saved);
    }

    // RESCHEDULED button (admin picks the new date/time)
    @Override
    @Transactional
    public AppointmentResponse adminReschedule(Long appointmentId, RescheduleRequest request) {
        Appointment appointment = findById(appointmentId);
        requireStatus(appointment, "reschedule", PENDING, APPROVED, RESCHEDULED);
        appointment.setDate(request.getNewDate());
        appointment.setAdminNote(clean(request.getNote()));
        appointment.setStatus(RESCHEDULED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyCustomer(
                saved,
                "Appointment rescheduled",
                "Your appointment was rescheduled to " + format(request.getNewDate()) + ".");
        return toResponse(saved);
    }

    // CANCELLED button
    @Override
    @Transactional
    public AppointmentResponse adminCancel(Long appointmentId, String note) {
        Appointment appointment = findById(appointmentId);
        requireStatus(appointment, "cancel", PENDING, APPROVED, RESCHEDULED);
        appointment.setAdminNote(clean(note));
        appointment.setStatus(CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyCustomer(
                saved, "Appointment cancelled", "Your appointment on " + format(saved.getDate()) + " was cancelled.");
        return toResponse(saved);
    }

    // Generic status change. PENDING and RESCHEDULED cannot be set here:
    // RESCHEDULED needs a new date (use adminReschedule), PENDING is only set by the customer.
    @Override
    @Transactional
    public AppointmentResponse updateStatus(
            Long appointmentId, AppointmentStatus status, String meetingLink, String note) {
        return switch (status) {
            case APPROVED -> approveAppointment(appointmentId, meetingLink, note);
            case CANCELLED -> adminCancel(appointmentId, note);
            case COMPLETED -> complete(appointmentId, note);
            case RESCHEDULED -> throw new BusinessRuleException("Use the reschedule endpoint to set a new date");
            case PENDING -> throw new BusinessRuleException("Status cannot be set back to PENDING");
        };
    }

    private AppointmentResponse complete(Long appointmentId, String note) {
        Appointment appointment = findById(appointmentId);
        requireStatus(appointment, "complete", APPROVED);
        if (appointment.getDate().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("This appointment hasn't happened yet. "
                    + "You can mark it completed after " + format(appointment.getDate()) + ".");
        }
        appointment.setAdminNote(clean(note));
        appointment.setStatus(COMPLETED);
        Appointment saved = appointmentRepository.save(appointment);
        notifyCustomer(
                saved, "Appointment completed", "Your appointment on " + format(saved.getDate()) + " is completed.");
        return toResponse(saved);
    }

    // ════════════════════════════ HELPERS ══════════════════════════════════

    private Appointment findById(Long appointmentId) {
        return appointmentRepository
                .findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
    }

    private Appointment findByIdAndUser(Long appointmentId, Long userId) {
        Appointment appointment = findById(appointmentId);
        if (!appointment.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }
        return appointment;
    }

    // Allows the action only when the current status is one of the allowed ones
    private void requireStatus(Appointment appointment, String action, AppointmentStatus... allowed) {
        for (AppointmentStatus s : allowed) {
            if (appointment.getStatus() == s) {
                return;
            }
        }
        throw new BusinessRuleException("Cannot " + action + " an appointment that is " + appointment.getStatus());
    }

    // In-app notification plus email for admin actions; includes the meeting link and note
    private void notifyCustomer(Appointment appointment, String subject, String message) {
        List<String> parts = new ArrayList<>(List.of(message));
        if (appointment.getStatus() == APPROVED && appointment.getMeetingLink() != null) {
            parts.add("Meeting link: " + appointment.getMeetingLink());
        }
        if (appointment.getAdminNote() != null) {
            parts.add("Note from GMC Lanka: " + appointment.getAdminNote());
        }
        User user = appointment.getUser();
        notifyUser(user.getUserId(), String.join(" ", parts));
        emailService.send(
                user.getEmail(),
                "GMC Lanka – " + subject,
                "Hi " + user.getName() + ",\n\n"
                        + String.join("\n\n", parts)
                        + "\n\nYou can view your appointments any time in your GMC Lanka account.\n\n"
                        + "— GMC Lanka");
    }

    // Only http(s) links are stored, so a link like "javascript:..." can never be rendered as a button
    private static String validateMeetingLink(String link) {
        String value = clean(link);
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null
                    || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new BusinessRuleException("Meeting link must be a valid http(s) URL");
            }
            return value;
        } catch (URISyntaxException e) {
            throw new BusinessRuleException("Meeting link must be a valid http(s) URL");
        }
    }

    // Only images uploaded through POST /api/appointments/images are accepted, never outside links
    private static List<String> uploadedImages(List<String> urls) {
        if (urls == null) {
            return new ArrayList<>();
        }
        List<String> images = new ArrayList<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            if (!url.matches("^/uploads/appointments/[A-Za-z0-9-]+\\.(png|jpe?g|webp|gif)$")) {
                throw new BusinessRuleException("Invalid image attachment");
            }
            if (!images.contains(url)) {
                images.add(url);
            }
        }
        if (images.size() > 6) {
            throw new BusinessRuleException("You can attach up to 6 images");
        }
        return images;
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String format(LocalDateTime date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }

    private static final String CUSTOMER_APPOINTMENTS_LINK = "/appointments";
    private static final String ADMIN_APPOINTMENTS_LINK = "/admin/appointments";

    private void notifyUser(Long userId, String message) {
        notificationService.sendNotification(userId, message, "APPOINTMENT", CUSTOMER_APPOINTMENTS_LINK);
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .appointmentId(a.getAppointmentId())
                .date(a.getDate())
                .message(a.getMessage())
                .status(a.getStatus())
                .userId(a.getUser().getUserId())
                .userName(a.getUser().getName())
                .meetingLink(a.getMeetingLink())
                .adminNote(a.getAdminNote())
                .imageUrls(a.getImageUrls() == null ? List.of() : new ArrayList<>(a.getImageUrls()))
                .categoryId(a.getCategory() == null ? null : a.getCategory().getCategoryId())
                .categoryName(a.getCategory() == null ? null : a.getCategory().getName())
                .build();
    }
}
