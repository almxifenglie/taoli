package com.arbitrage.lofqdii.data.model

import org.junit.Assert.*
import org.junit.Test

class FundTest {

    @Test
    fun displaySubscribeLimit_correctFormat() {
        val fund1 = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            subscribeLimit = 10000.0
        )
        assertEquals("1.00万", fund1.displaySubscribeLimit)
    }

    @Test
    fun displaySubscribeLimit_largeValue() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            subscribeLimit = 100000000.0
        )
        assertEquals("10000.00万", fund.displaySubscribeLimit)
    }

    @Test
    fun displaySubscribeLimit_null() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            subscribeLimit = null
        )
        assertEquals("--", fund.displaySubscribeLimit)
    }

    @Test
    fun displaySubscribeLimit_zeroOrNegative() {
        val fund1 = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            subscribeLimit = 0.0
        )
        assertEquals("无限额", fund1.displaySubscribeLimit)

        val fund2 = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            subscribeLimit = -100.0
        )
        assertEquals("无限额", fund2.displaySubscribeLimit)
    }

    @Test
    fun displayMarketPrice_correctFormat() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            marketPrice = 1.2345
        )
        assertEquals("1.234", fund.displayMarketPrice)
    }

    @Test
    fun displayMarketPrice_null() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            marketPrice = null
        )
        assertEquals("--", fund.displayMarketPrice)
    }

    @Test
    fun displayNav_correctFormat() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            nav = 1.23456
        )
        assertEquals("1.2346", fund.displayNav)
    }

    @Test
    fun displayNav_null() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            nav = null
        )
        assertEquals("--", fund.displayNav)
    }

    @Test
    fun displayVolume_formatsCorrectly() {
        val fund1 = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            volume = 1500000000L
        )
        assertEquals("15.00亿", fund1.displayVolume)

        val fund2 = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            volume = 150000L
        )
        assertEquals("15.00万", fund2.displayVolume)

        val fund3 = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            volume = 1500L
        )
        assertEquals("1500", fund3.displayVolume)
    }

    @Test
    fun displayVolume_null() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            volume = null
        )
        assertEquals("--", fund.displayVolume)
    }

    @Test
    fun displayT1PremiumRate_correctFormat() {
        val fund1 = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            t1PremiumRate = 5.5
        )
        assertEquals("+5.50%", fund1.displayT1PremiumRate)

        val fund2 = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            t1PremiumRate = -3.25
        )
        assertEquals("-3.25%", fund2.displayT1PremiumRate)
    }

    @Test
    fun displayT1PremiumRate_null() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            t1PremiumRate = null
        )
        assertEquals("--", fund.displayT1PremiumRate)
    }

    @Test
    fun hasValidData_bothPresent() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            marketPrice = 1.0,
            nav = 1.0
        )
        assertTrue(fund.hasValidData)
    }

    @Test
    fun hasValidData_priceMissing() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            marketPrice = null,
            nav = 1.0
        )
        assertFalse(fund.hasValidData)
    }

    @Test
    fun hasValidData_navMissing() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            marketPrice = 1.0,
            nav = null
        )
        assertFalse(fund.hasValidData)
    }

    @Test
    fun displayDataSource_showsSourcesCorrectly() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF,
            priceSource = "EastMoney",
            navSource = "TianTian",
            subscribeSource = "EastMoney_F10"
        )
        assertEquals("价格:EastMoney 净值:TianTian 申购:EastMoney_F10", fund.displayDataSource)
    }

    @Test
    fun displayDataSource_noSources() {
        val fund = Fund(
            code = "161725",
            name = "test",
            type = FundType.LOF
        )
        assertEquals("--", fund.displayDataSource)
    }
}