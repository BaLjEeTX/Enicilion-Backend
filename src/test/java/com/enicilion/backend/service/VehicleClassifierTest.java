package com.enicilion.backend.service;

import com.enicilion.backend.applications.service.VehicleClassifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleClassifierTest {

    @Test
    void testDetectVehicleClassJdm() {
        String classification = VehicleClassifier.detectVehicleClass("Toyota", "Supra MK4", "none", "2JZ-GTE");
        assertEquals("JDM", classification);
    }

    @Test
    void testDetectVehicleClassEuro() {
        String classification = VehicleClassifier.detectVehicleClass("BMW", "M3 E46", "none", "S54");
        assertEquals("Euro", classification);
    }

    @Test
    void testDetectVehicleClassModified() {
        String classification = VehicleClassifier.detectVehicleClass("Honda", "Civic", "stage 2 turbo kit with roll cage", "B16");
        assertEquals("Modified", classification);
    }

    @Test
    void testDetectVehicleClassStock() {
        String classification = VehicleClassifier.detectVehicleClass("Hyundai", "Elantra", "none", "Stock 2.0L");
        assertEquals("Stock", classification);
    }

    @Test
    void testDetectVehicleClassNullSafety() {
        String classification = VehicleClassifier.detectVehicleClass(null, "Supra", null, null);
        assertEquals("JDM", classification);
    }
}
