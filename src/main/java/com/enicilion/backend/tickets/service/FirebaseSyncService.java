package com.enicilion.backend.tickets.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class FirebaseSyncService {

    @Async
    public void syncCheckInState(String ticketCode, OffsetDateTime checkedInAt, int scanCount) {
        log.info("Starting async Firebase sync check-in for ticket: {}", ticketCode);
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase not initialized. Mocking check-in sync for ticket: {}", ticketCode);
            return;
        }

        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentReference docRef = db.collection("tickets").document(ticketCode);

            Map<String, Object> data = new HashMap<>();
            data.put("status", "checked_in");
            data.put("checkedInAt", checkedInAt != null ? checkedInAt.toString() : null);
            data.put("scanCount", scanCount);
            data.put("blocked", false);
            data.put("gate", "tickets_api");
            data.put("operatorId", "scanner_terminal");

            ApiFuture<WriteResult> result = docRef.set(data, SetOptions.merge());
            com.google.api.core.ApiFutures.addCallback(result, new com.google.api.core.ApiFutureCallback<WriteResult>() {
                @Override
                public void onSuccess(WriteResult writeResult) {
                    log.info("Firebase check-in sync completed for ticket: {} at {}", ticketCode, writeResult.getUpdateTime());
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Failed to sync check-in state to Firebase for ticket: " + ticketCode, t);
                }
            }, com.google.common.util.concurrent.MoreExecutors.directExecutor());
        } catch (Exception e) {
            log.error("Failed to initiate sync check-in state to Firebase for ticket: " + ticketCode, e);
        }
    }

    @Async
    public void syncBlockState(String ticketCode, boolean blocked, int scanCount) {
        log.info("Starting async Firebase sync block state for ticket: {}", ticketCode);
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase not initialized. Mocking block sync for ticket: {}", ticketCode);
            return;
        }

        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentReference docRef = db.collection("tickets").document(ticketCode);

            Map<String, Object> data = new HashMap<>();
            data.put("status", blocked ? "cancelled" : "checked_in");
            data.put("scanCount", scanCount);
            data.put("blocked", blocked);
            data.put("gate", "tickets_api");
            data.put("operatorId", "scanner_terminal");

            ApiFuture<WriteResult> result = docRef.set(data, SetOptions.merge());
            com.google.api.core.ApiFutures.addCallback(result, new com.google.api.core.ApiFutureCallback<WriteResult>() {
                @Override
                public void onSuccess(WriteResult writeResult) {
                    log.info("Firebase block sync completed for ticket: {} at {}", ticketCode, writeResult.getUpdateTime());
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Failed to sync block state to Firebase for ticket: " + ticketCode, t);
                }
            }, com.google.common.util.concurrent.MoreExecutors.directExecutor());
        } catch (Exception e) {
            log.error("Failed to initiate sync block state to Firebase for ticket: " + ticketCode, e);
        }
    }

    @Async
    public void syncRevertCheckInState(String ticketCode, int scanCount) {
        log.info("Starting async Firebase sync revert check-in for ticket: {}", ticketCode);
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase not initialized. Mocking revert check-in sync for ticket: {}", ticketCode);
            return;
        }

        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentReference docRef = db.collection("tickets").document(ticketCode);

            Map<String, Object> data = new HashMap<>();
            data.put("status", "paid");
            data.put("checkedInAt", null);
            data.put("scanCount", scanCount);
            data.put("blocked", false);
            data.put("gate", "tickets_api");
            data.put("operatorId", "scanner_terminal");

            ApiFuture<WriteResult> result = docRef.set(data, SetOptions.merge());
            com.google.api.core.ApiFutures.addCallback(result, new com.google.api.core.ApiFutureCallback<WriteResult>() {
                @Override
                public void onSuccess(WriteResult writeResult) {
                    log.info("Firebase revert check-in sync completed for ticket: {} at {}", ticketCode, writeResult.getUpdateTime());
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Failed to sync revert check-in state to Firebase for ticket: " + ticketCode, t);
                }
            }, com.google.common.util.concurrent.MoreExecutors.directExecutor());
        } catch (Exception e) {
            log.error("Failed to initiate sync revert check-in state to Firebase for ticket: " + ticketCode, e);
        }
    }
}
