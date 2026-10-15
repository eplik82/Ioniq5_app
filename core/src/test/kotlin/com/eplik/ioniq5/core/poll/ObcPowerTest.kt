package com.eplik.ioniq5.core.poll

import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import org.junit.Assert.assertEquals
import org.junit.Test

/** AC-laadimine 2026-10-10 (11 kW kodulaadija, 3 faasi): kasutegur peab tulema ~94 %, mitte ~100 %. */
class ObcPowerTest {

    private val readings = mapOf(
        P.OBC_AC_VOLTAGE to 383.3,
        P.OBC_AC_CURRENT_1 to 15.82,
        P.OBC_AC_CURRENT_2 to 15.82,
        P.OBC_AC_CURRENT_3 to 15.58,
        P.OBC_DC_VOLTAGE to 734.4,
        P.OBC_DC_CURRENT to 13.35,
    ).mapValues { Reading(it.value, 0) }

    @Test
    fun threePhaseInputPower() = assertEquals(10.45, readings.acInputPowerKw()!!, 0.01)

    @Test
    fun efficiency() = assertEquals(0.936, readings.obcOutputPowerKw()!! / readings.acInputPowerKw()!!, 0.005)

    @Test
    fun obcActiveMeansAc() {
        assertEquals(true, readings.obcActive())
        assertEquals(false, (readings + (P.OBC_DC_CURRENT to Reading(0.0, 0))).obcActive())
        assertEquals(null, emptyMap<String, Reading>().obcActive())
    }
}
