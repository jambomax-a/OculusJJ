package com.oculus.jj.util;

/**
 * Utility class for common string operations.
 */
public class StringUtils {

    /**
     * Compares two strings numerically. 
     * If the strings represent numbers, they are compared as such (e.g., "10" > "2").
     * Otherwise, it falls back to standard lexicographical comparison.
     * This is a port of the C++/Objective-C stringNumericCompare utility.
     * 
     * @param s1 The first string.
     * @param s2 The second string.
     * @return A negative integer, zero, or a positive integer as this string 
     *         is less than, equal to, or greater than the specified string.
     */
    public static int numericCompare(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return s1 == s2 ? 0 : (s1 == null ? -1 : 1);
        }

        try {
            // Attempt to parse as double for numerical comparison.
            // We use Double.parseDouble which handles decimals and scientific notation.
            double d1 = Double.parseDouble(s1.trim());
            double d2 = Double.parseDouble(s2.trim());
            return Double.compare(d1, d2);
        } catch (NumberFormatException e) {
            // Fall back to standard lexicographical comparison if not numeric.
            return s1.compareTo(s2);
        }
    }

    /**
     * Utility for handling specific logic required by HOROS string additions.
     */
    public static String truncateToFitSize(String str, int size) {
        if (str == null || str.length() <= size) {
            return str;
        }
        return str.substring(0, size);
    }

    private StringUtils() {
        // Utility class - no instantiation needed.
    }
}
