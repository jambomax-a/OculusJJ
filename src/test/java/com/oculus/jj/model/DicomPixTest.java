package com.oculus.jj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DicomPixTest {

    @Test
    void voiLutClampsInputAndScalesEightBitOutput() {
        int[] lut = {0, 127, 255};

        assertEquals(0, DicomPix.mapVOILUTValue(9, 10, 8, lut, false));
        assertEquals(0, DicomPix.mapVOILUTValue(10, 10, 8, lut, false));
        assertEquals(127, DicomPix.mapVOILUTValue(11, 10, 8, lut, false));
        assertEquals(255, DicomPix.mapVOILUTValue(12, 10, 8, lut, false));
        assertEquals(255, DicomPix.mapVOILUTValue(20, 10, 8, lut, false));
    }

    @Test
    void voiLutScalesSixteenBitOutputAndInvertsMonochromeOne() {
        int[] lut = {0, 32768, 65535};

        assertEquals(128, DicomPix.mapVOILUTValue(1, 0, 16, lut, false));
        assertEquals(127, DicomPix.mapVOILUTValue(1, 0, 16, lut, true));
    }

    @Test
    void suvScaleUsesPatientWeightAndAdministeredDose() {
        double factor = DicomPix.calculateSUVScaleFactor(70.0, 370_000_000.0, 6586.0, 0.0);

        assertEquals(70_000.0 / 370_000_000.0, factor, 1e-12);
    }

    @Test
    void suvScaleCorrectsDoseForElapsedHalfLife() {
        double initial = DicomPix.calculateSUVScaleFactor(70.0, 370_000_000.0, 6586.0, 0.0);
        double afterOneHalfLife = DicomPix.calculateSUVScaleFactor(70.0, 370_000_000.0, 6586.0, 6586.0);

        assertEquals(initial * 2.0, afterOneHalfLife, 1e-12);
    }

    @Test
    void suvScaleRejectsMissingOrInvalidParameters() {
        assertTrue(Double.isNaN(DicomPix.calculateSUVScaleFactor(70.0, 0.0, 6586.0, 0.0)));
        assertTrue(Double.isNaN(DicomPix.calculateSUVScaleFactor(70.0, 370_000_000.0, 0.0, 0.0)));
    }

    @Test
    void patientUidUsesSelectedFieldsAndKeepsIdOnlyCompatibility() {
        java.util.Date birthDate = java.util.Date.from(java.time.LocalDate.of(2018, 4, 12)
                .atStartOfDay(java.time.ZoneId.of("UTC")).toInstant());
        String legacy = DicomDatabase.createPatientUID("Test^Patient", "PET-42", birthDate, true, false, false);
        String selectedFields = DicomDatabase.createPatientUID("Test^Patient", "PET-42", birthDate, true, true, true);
        String normalized = DicomDatabase.createPatientUID(" test^patient ", " pet-42 ", birthDate, true, true, true);

        assertEquals("PATIENT_PET-42", legacy);
        assertEquals(selectedFields, normalized);
        assertNotEquals(legacy, selectedFields);
        assertEquals("PATIENT_UNKNOWN", DicomDatabase.createPatientUID(null, null, null, false, false, false));
    }
}
