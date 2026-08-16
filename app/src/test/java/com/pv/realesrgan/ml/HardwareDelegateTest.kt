package com.pv.realesrgan.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareDelegateTest {

    @Test
    fun testHardwareDelegateValues() {
        val delegates = HardwareDelegate.values()
        assertEquals(3, delegates.size)
    }

    @Test
    fun testHardwareDelegateProperties() {
        val auto = HardwareDelegate.AUTO
        assertTrue(auto.displayName.contains("Auto"))
        assertTrue(auto.description.contains("NPU / GPU with CPU fallback"))

        val npu = HardwareDelegate.NPU_NNAPI
        assertTrue(npu.displayName.contains("NPU"))
        assertTrue(npu.description.contains("Dedicated Neural Processing Unit"))

        val cpu = HardwareDelegate.CPU
        assertTrue(cpu.displayName.contains("CPU"))
        assertTrue(cpu.description.contains("Reliable multi-threaded CPU execution"))
    }

    @Test
    fun testValueOf() {
        assertEquals(HardwareDelegate.AUTO, HardwareDelegate.valueOf("AUTO"))
        assertEquals(HardwareDelegate.NPU_NNAPI, HardwareDelegate.valueOf("NPU_NNAPI"))
        assertEquals(HardwareDelegate.CPU, HardwareDelegate.valueOf("CPU"))
    }
}
