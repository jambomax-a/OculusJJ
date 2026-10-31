package com.oculus.jj.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DicomDatabaseTest {

    @Test
    void routingValueMatcherSupportsCaseInsensitiveExactAndWildcardRules() {
        assertTrue(DicomDatabase.routingValueMatches("CT", "ct"));
        assertTrue(DicomDatabase.routingValueMatches("CT Chest", "CT*"));
        assertTrue(DicomDatabase.routingValueMatches("PAT-001", "pat-*1"));
        assertFalse(DicomDatabase.routingValueMatches("MR Brain", "CT*"));
        assertFalse(DicomDatabase.routingValueMatches("CT", "CT*MR"));
    }
}
