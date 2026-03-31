from __future__ import annotations

import json
import unittest

from desktop_server.protocol import build_pairing_payload, decode_message, encode_message


class ProtocolTests(unittest.TestCase):
    def test_encode_and_decode_round_trip(self) -> None:
        payload = {"type": "heartbeat", "session_id": "abc"}

        raw = encode_message(payload)
        restored = decode_message(raw)

        self.assertEqual(payload, restored)

    def test_build_pairing_payload_contains_required_fields(self) -> None:
        payload = json.loads(
            build_pairing_payload(
                host_ip="192.168.0.12",
                port=50555,
                server_name="Workstation",
                token="token-123",
            )
        )

        self.assertEqual("192.168.0.12", payload["server_ip"])
        self.assertEqual(50555, payload["port"])
        self.assertEqual("Workstation", payload["server_name"])
        self.assertEqual("token-123", payload["token"])
        self.assertIn("issued_at", payload)


if __name__ == "__main__":
    unittest.main()

