package com.enicilion.backend.applications.service;

import com.enicilion.backend.applications.entity.*;
import com.enicilion.backend.applications.repository.ApplicationRepository;
import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.tickets.entity.Event;
import com.enicilion.backend.tickets.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final EventRepository eventRepository;
    private final ImageProcessingService imageProcessingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public EventApplication submitApplication(
            UUID eventId,
            User user,
            String responsesJson,
            List<MultipartFile> photos) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        // Check unique constraint: event_id + user_id
        if (applicationRepository.findByEventIdAndUserId(eventId, user.getId()).isPresent()) {
            throw new BadValidationException("You have already submitted an application for this event.");
        }

        // Process photos and collect storage paths
        List<String> photoPaths = new ArrayList<>();
        if (photos != null && !photos.isEmpty()) {
            for (MultipartFile file : photos) {
                if (file.isEmpty()) continue;
                try {
                    ImageProcessingService.ProcessedImageResult processed = imageProcessingService.processImage(file);
                    photoPaths.add(processed.getStoragePath());
                } catch (Exception e) {
                    log.error("Failed to process uploaded photo during application submission", e);
                }
            }
        }

        // Parse responses JSON
        Map<String, Object> responses = new HashMap<>();
        try {
            responses = objectMapper.readValue(responsesJson, Map.class);
        } catch (Exception e) {
            throw new BadValidationException("Invalid JSON format in responses parameter.");
        }

        // If photos were processed, put them under the "photos" key
        if (!photoPaths.isEmpty()) {
            responses.put("photos", photoPaths);
        }

        String serializedResponses;
        try {
            serializedResponses = objectMapper.writeValueAsString(responses);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize registration responses to JSON", e);
        }

        // Create generalized Event Application
        EventApplication application = EventApplication.builder()
                .event(event)
                .user(user)
                .status(ApplicationStatus.pending)
                .registrationResponses(serializedResponses)
                .build();

        return applicationRepository.save(application);
    }

    @Transactional
    public EventApplication reviewApplication(UUID applicationId, ApplicationStatus status, String adminNotes, User reviewer) {
        EventApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        if (application.getStatus() != ApplicationStatus.pending) {
            throw new BadValidationException("Application has already been reviewed.");
        }

        application.setStatus(status);
        application.setReviewedBy(reviewer);
        application.setReviewedAt(OffsetDateTime.now());
        application.setAdminNotes(adminNotes);
        
        return applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllApplicationsMapped() {
        List<EventApplication> applications = applicationRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (EventApplication app : applications) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", app.getId().toString());
            map.put("drifterName", app.getUser() != null ? app.getUser().getFullName() : "Unknown");
            map.put("drifterEmail", app.getUser() != null ? app.getUser().getEmail() : "Unknown");
            map.put("status", app.getStatus().name());
            
            // Extract dynamic responses if present
            if (app.getRegistrationResponses() != null) {
                try {
                    Map<String, Object> responsesMap = objectMapper.readValue(app.getRegistrationResponses(), Map.class);
                    // Write all custom responses dynamically into the map
                    map.putAll(responsesMap);
                } catch (Exception e) {
                    log.error("Failed to parse registration responses JSON for application: " + app.getId(), e);
                }
            }
            result.add(map);
        }
        return result;
    }
}
