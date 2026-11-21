package dev.clamp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServicePickTest {
    private val local = setOf("192.168.1.23")

    @Test fun newest_wins() {
        val r = ServicePick.pick(listOf(MdnsService("a", 1000, "192.168.1.23", 1), MdnsService("b", 2000, "192.168.1.23", 2)), local)
        assertEquals(2000, r?.port)
    }

    @Test fun foreign_host_ignored_null_host_kept() {
        assertNull(ServicePick.pick(listOf(MdnsService("x", 1000, "192.168.1.50", 1)), local))
        assertNull(ServicePick.pick(listOf(MdnsService("x", 1000, null, 1)), local))
        assertEquals(1000, ServicePick.pick(listOf(MdnsService("x", 1000, "192.168.1.23%wlan0", 1)), local)?.port)
    }

    @Test fun bad_port_dropped_loopback_local() {
        assertNull(ServicePick.pick(listOf(MdnsService("x", 0, "127.0.0.1", 1), MdnsService("y", 70000, "127.0.0.1", 2)), local))
        assertEquals(5, ServicePick.pick(listOf(MdnsService("x", 5, "127.0.0.1", 1)), emptySet())?.port)
    }
}
