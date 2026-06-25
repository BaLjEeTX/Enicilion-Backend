package com.enicilion.backend.tickets.service;

import com.enicilion.backend.tickets.dto.CheckinFeedEventDto;
import com.enicilion.backend.tickets.entity.CheckinEvent;
import com.enicilion.backend.tickets.entity.SpectatorTicket;
import com.enicilion.backend.tickets.entity.TicketStatus;
import com.enicilion.backend.tickets.repository.CheckinRepository;
import com.enicilion.backend.tickets.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LiveCheckinService {

    private final CheckinRepository checkinRepository;
    private final TicketRepository ticketRepository;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("handshake")
                    .data("Connected to Enicilion Live Check-in Stream"));
        } catch (IOException e) {
            log.error("Failed to send handshake to client emitter", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Async
    public void broadcast(CheckinFeedEventDto event) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("checkin")
                        .data(event));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    public void publishCheckin(CheckinEvent attempt, SpectatorTicket ticket, int totalCheckedIn) {
        String buyerName = "";
        String buyerEmail = "";
        String tierName = "";
        if (ticket != null) {
            if (ticket.getUser() != null) {
                buyerName = ticket.getUser().getFullName();
                buyerEmail = ticket.getUser().getEmail();
            }
            if (ticket.getTier() != null) {
                tierName = ticket.getTier().getName();
            }
        }

        CheckinFeedEventDto eventDto = CheckinFeedEventDto.builder()
                .ticketCode(attempt.getTicketCode())
                .action(attempt.getAction())
                .gate(attempt.getGate())
                .reason(attempt.getReason())
                .buyerName(buyerName)
                .buyerEmail(buyerEmail)
                .tierName(tierName)
                .createdAt(attempt.getCreatedAt())
                .totalCheckedIn(totalCheckedIn)
                .build();

        broadcast(eventDto);
    }

    public List<CheckinFeedEventDto> getRecentCheckins() {
        List<CheckinEvent> events = checkinRepository.findTop50ByOrderByCreatedAtDesc();
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // Gather all ticket codes to bulk-fetch and prevent N+1 queries
        Set<String> ticketCodes = events.stream()
                .map(CheckinEvent::getTicketCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<SpectatorTicket> tickets = ticketRepository.findByTicketCodeIn(ticketCodes);
        Map<String, SpectatorTicket> ticketMap = tickets.stream()
                .collect(Collectors.toMap(SpectatorTicket::getTicketCode, t -> t, (t1, t2) -> t1));

        int totalCheckedIn = ticketRepository.countByStatusIn(List.of(TicketStatus.checked_in));

        List<CheckinFeedEventDto> dtoList = new ArrayList<>();
        for (CheckinEvent event : events) {
            SpectatorTicket ticket = ticketMap.get(event.getTicketCode());
            String buyerName = "";
            String buyerEmail = "";
            String tierName = "";
            if (ticket != null) {
                if (ticket.getUser() != null) {
                    buyerName = ticket.getUser().getFullName();
                    buyerEmail = ticket.getUser().getEmail();
                }
                if (ticket.getTier() != null) {
                    tierName = ticket.getTier().getName();
                }
            }

            dtoList.add(CheckinFeedEventDto.builder()
                    .ticketCode(event.getTicketCode())
                    .action(event.getAction())
                    .gate(event.getGate())
                    .reason(event.getReason())
                    .buyerName(buyerName)
                    .buyerEmail(buyerEmail)
                    .tierName(tierName)
                    .createdAt(event.getCreatedAt())
                    .totalCheckedIn(totalCheckedIn)
                    .build());
        }

        return dtoList;
    }
}
