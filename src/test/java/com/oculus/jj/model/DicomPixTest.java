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

    private static double[] moments(double... values) {
        double sum = 0, sumSq = 0, sumCube = 0, sumFourth = 0;
        for (double v : values) {
            sum += v;
            sumSq += v * v;
            sumCube += v * v * v;
            sumFourth += v * v * v * v;
        }
        return DicomPix.computeSkewKurt(sum, sumSq, sumCube, sumFourth, values.length);
    }

    @Test
    void skewKurtIsZeroForSymmetricDistribution() {
        // 対称分布 (1..5) → 歪度0
        double[] sk = moments(1, 2, 3, 4, 5);
        assertEquals(0.0, sk[0], 1e-9);
        // 尖度 = μ4/σ^4 = 21.7 / (2)^2 = 1.7（正規分布=3から見て尖った分布）
        assertEquals(1.7, sk[1], 1e-9);
    }

    @Test
    void skewKurtDetectsRightSkew() {
        double[] sk = moments(1, 1, 1, 2, 3, 10);
        assertTrue(sk[0] > 0.5, "右歪分布は正の歪度になるべき: " + sk[0]);
    }

    @Test
    void skewKurtConstantSamplesYieldZero() {
        double[] sk = moments(5, 5, 5, 5);
        assertEquals(0.0, sk[0], 1e-12);
        assertEquals(0.0, sk[1], 1e-12);
    }

    @Test
    void skewKurtEmptyInputYieldsZero() {
        double[] sk = DicomPix.computeSkewKurt(0, 0, 0, 0, 0);
        assertEquals(0.0, sk[0]);
        assertEquals(0.0, sk[1]);
    }

    @Test
    void dicomPrintBuildersPopulateFilmSessionFilmBoxAndImageBoxAttributes() {
        org.dcm4che3.data.Attributes session = DicomService.buildFilmSessionAttributes();
        assertTrue(session.containsValue(org.dcm4che3.data.Tag.NumberOfCopies));
        assertTrue(session.containsValue(org.dcm4che3.data.Tag.PrintPriority));
        assertTrue(session.containsValue(org.dcm4che3.data.Tag.MediumType));
        assertTrue(session.containsValue(org.dcm4che3.data.Tag.FilmDestination));

        org.dcm4che3.data.Attributes box = DicomService.buildFilmBoxAttributes("1.2.3.4", 2, 1);
        assertEquals("STANDARD\\2,1", box.getString(org.dcm4che3.data.Tag.ImageDisplayFormat));
        assertEquals("1.2.3.4", box.getSequence(org.dcm4che3.data.Tag.ReferencedFilmSessionSequence)
                .get(0).getString(org.dcm4che3.data.Tag.ReferencedSOPInstanceUID));

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 3, java.awt.image.BufferedImage.TYPE_INT_RGB);
        org.dcm4che3.data.Attributes imageBox = DicomService.buildGrayscaleImageBoxAttributes(img, 2);
        assertEquals(2, imageBox.getInt(org.dcm4che3.data.Tag.ImageBoxPosition, 0));
        org.dcm4che3.data.Attributes pixelItem = imageBox.getSequence(org.dcm4che3.data.Tag.BasicGrayscaleImageSequence).get(0);
        assertEquals(3, pixelItem.getInt(org.dcm4che3.data.Tag.Rows, 0));
        assertEquals(4, pixelItem.getInt(org.dcm4che3.data.Tag.Columns, 0));
        assertEquals("MONOCHROME2", pixelItem.getString(org.dcm4che3.data.Tag.PhotometricInterpretation));
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
