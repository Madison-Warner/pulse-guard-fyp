package com.example.pulseguard.logs


import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HrLogAggregatorTest {
    @Test
    fun bucketIsNotCreatedBeforeDurationPassed() {
        val aggregator = HrLogAggregator(bucketDurationMillis = 10_000L)

        val start = 1000L

        val first = aggregator.addSample(start, 70)
        val second = aggregator.addSample(start + 5_000L, 75)

        assertNull(first)
        assertNull(second)
    }

    @Test
    fun bucketIsCreatedAfterDurationPasses(){
        val aggregator = HrLogAggregator(bucketDurationMillis = 10_000L)

        val start = 1000L

        aggregator.addSample(start, 70)
        aggregator.addSample(start + 5_000L, 75)

        val bucket = aggregator.addSample(start + 11_000L, 80)

        assertNotNull(bucket)
    }

    @Test
    fun averageBpmIsCalculatedCorrectly(){
        val aggregator = HrLogAggregator(bucketDurationMillis = 10_000L)

        val start = 1000L

        aggregator.addSample(start, 70)
        aggregator.addSample(start + 2_000L, 80)

        val bucket = aggregator.addSample(start + 11_000L, 90)

        // The bucket contains the completed samples before the rollover sample.
        // So average is (70 + 80) / 2 = 75
        assertEquals(75, bucket?.avgBpm)
    }

    @Test
    fun minAndMaxBpmAreCalculatedCorrectly() {
        val aggregator = HrLogAggregator(bucketDurationMillis = 10_000L)

        val start = 1000L

        aggregator.addSample(start, 65)
        aggregator.addSample(start + 2_000L, 90)
        aggregator.addSample(start + 4_000L, 72)

        val bucket = aggregator.addSample(start + 11_000L, 80)

        assertEquals(65, bucket?.minBpm)
        assertEquals(90, bucket?.maxBpm)
    }

    @Test
    fun invalidBpmIsIgnored() {
        val aggregator = HrLogAggregator(bucketDurationMillis = 10_000L)

        val start = 1000L

        val result = aggregator.addSample(start, 0)

        assertNull(result)
    }
}