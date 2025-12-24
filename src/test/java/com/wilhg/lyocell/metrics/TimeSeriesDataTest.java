package com.wilhg.lyocell.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TimeSeriesDataTest {

    @Test
    void testConstructorAndGetters() {
        long timestamp = 1640995200000L; // 2022-01-01 00:00:00 UTC
        long successfulRequests = 100;
        long failedRequests = 5;

        TimeSeriesData data = new TimeSeriesData(timestamp, successfulRequests, failedRequests);

        assertEquals(timestamp, data.timestamp());
        assertEquals(successfulRequests, data.successfulRequests());
        assertEquals(failedRequests, data.failedRequests());
    }

    @Test
    void testZeroValues() {
        TimeSeriesData data = new TimeSeriesData(0, 0, 0);

        assertEquals(0, data.timestamp());
        assertEquals(0, data.successfulRequests());
        assertEquals(0, data.failedRequests());
    }

    @Test
    void testLargeValues() {
        long maxLong = Long.MAX_VALUE;
        TimeSeriesData data = new TimeSeriesData(maxLong, maxLong, maxLong);

        assertEquals(maxLong, data.timestamp());
        assertEquals(maxLong, data.successfulRequests());
        assertEquals(maxLong, data.failedRequests());
    }

    @Test
    void testNegativeValues() {
        // Records don't prevent negative values, but let's test they work
        TimeSeriesData data = new TimeSeriesData(-1000, -10, -5);

        assertEquals(-1000, data.timestamp());
        assertEquals(-10, data.successfulRequests());
        assertEquals(-5, data.failedRequests());
    }

    @Test
    void testEquality() {
        TimeSeriesData data1 = new TimeSeriesData(1000, 50, 2);
        TimeSeriesData data2 = new TimeSeriesData(1000, 50, 2);
        TimeSeriesData data3 = new TimeSeriesData(1001, 50, 2);

        assertEquals(data1, data2);
        assertNotEquals(data1, data3);
        assertNotEquals(data1, null);
        assertNotEquals(data1, "not a record");
    }

    @Test
    void testHashCode() {
        TimeSeriesData data1 = new TimeSeriesData(1000, 50, 2);
        TimeSeriesData data2 = new TimeSeriesData(1000, 50, 2);
        TimeSeriesData data3 = new TimeSeriesData(1001, 50, 2);

        assertEquals(data1.hashCode(), data2.hashCode());
        assertNotEquals(data1.hashCode(), data3.hashCode());
    }

    @Test
    void testToString() {
        TimeSeriesData data = new TimeSeriesData(1000, 50, 2);
        String toString = data.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("TimeSeriesData"));
        assertTrue(toString.contains("1000"));
        assertTrue(toString.contains("50"));
        assertTrue(toString.contains("2"));
    }

    @Test
    void testDifferentInstances() {
        TimeSeriesData data1 = new TimeSeriesData(1000, 50, 2);
        TimeSeriesData data2 = new TimeSeriesData(1000, 51, 2); // Different successfulRequests
        TimeSeriesData data3 = new TimeSeriesData(1000, 50, 3); // Different failedRequests
        TimeSeriesData data4 = new TimeSeriesData(1001, 50, 2); // Different timestamp

        assertNotEquals(data1, data2);
        assertNotEquals(data1, data3);
        assertNotEquals(data1, data4);

        assertNotEquals(data1.hashCode(), data2.hashCode());
        assertNotEquals(data1.hashCode(), data3.hashCode());
        assertNotEquals(data1.hashCode(), data4.hashCode());
    }

    @Test
    void testTotalRequests() {
        TimeSeriesData data = new TimeSeriesData(1000, 80, 20);
        long totalRequests = data.successfulRequests() + data.failedRequests();

        assertEquals(100, totalRequests);
    }

    @Test
    void testSuccessRate() {
        TimeSeriesData data = new TimeSeriesData(1000, 80, 20);
        double successRate = (double) data.successfulRequests() /
                           (data.successfulRequests() + data.failedRequests());

        assertEquals(0.8, successRate, 0.001);
    }

    @Test
    void testZeroTotalRequests() {
        TimeSeriesData data = new TimeSeriesData(1000, 0, 0);
        long totalRequests = data.successfulRequests() + data.failedRequests();

        assertEquals(0, totalRequests);
    }
}
