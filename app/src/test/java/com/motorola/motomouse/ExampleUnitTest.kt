package com.motorola.motomouse

import com.motorola.motomouse.data.PairingInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun qrPayload_isParsedSuccessfully() {
        val payload = """
            {
              "version": 1,
              "server_ip": "192.168.1.20",
              "port": 50555,
              "token": "pair-token",
              "server_name": "My Laptop",
              "issued_at": 1710000000000
            }
        """.trimIndent()

        val pairingInfo = PairingInfo.fromQrPayload(payload).getOrThrow()

        assertEquals("192.168.1.20", pairingInfo.serverIp)
        assertEquals(50555, pairingInfo.port)
        assertEquals("pair-token", pairingInfo.token)
        assertEquals("My Laptop", pairingInfo.serverName)
    }

    @Test
    fun storedJson_roundTripsSuccessfully() {
        val source = PairingInfo(
            version = 1,
            serverIp = "10.0.0.8",
            port = 50555,
            token = "abc123",
            serverName = "Office PC",
            issuedAt = 1710000000001,
        )

        val restored = PairingInfo.fromStoredJson(source.toJson())

        assertNotNull(restored)
        assertEquals(source, restored)
    }

    @Test
    fun malformedPayload_returnsFailure() {
        val result = PairingInfo.fromQrPayload("not-json")

        assertTrue(result.isFailure)
    }
}