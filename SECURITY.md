# Security policy

SwagBench is a local benchmark harness, not a hardened public game server.

Please report suspected command-permission bypasses, unsafe file writes, path traversal, report-data disclosure, unbounded resource consumption outside declared benchmark behavior, or network exposure caused by the included scripts through the contact method on the repository owner's GitHub profile.

Do not attach private mod lists, server logs, world data, access tokens, host inventories, or unsanitized benchmark reports to public issues.

The bundled scripts run an offline-mode Forge server bound to loopback for local testing. Never expose that process to an untrusted network.
