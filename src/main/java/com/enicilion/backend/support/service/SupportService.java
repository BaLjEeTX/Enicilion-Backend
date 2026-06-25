package com.enicilion.backend.support.service;

import com.enicilion.backend.support.entity.SupportTicket;
import com.enicilion.backend.support.repository.SupportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportRepository supportRepository;

    @Transactional
    public SupportTicket createSupportTicket(
            String category, 
            String message, 
            String name, 
            String phone, 
            String email, 
            UUID userId) {
        
        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .category(category)
                .message(message)
                .contactName(name)
                .contactPhone(phone)
                .contactEmail(email)
                .status("open")
                .metadata("{}")
                .build();
        
        return supportRepository.save(ticket);
    }
}
