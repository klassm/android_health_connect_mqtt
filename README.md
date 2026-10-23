# WalkingPad → Health Connect (Android)

An Android companion app for the
[WalkingPad macOS Client](https://github.com/klassm/walkingpad_macos_client):
it subscribes to the MQTT topic that the macOS client publishes and writes the
steps into [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect),
so your WalkingPad steps show up in Google Fit, Samsung Health, and any other
Health Connect–integrated app on your phone.

```
WalkingPad ──BLE──▶ macOS Client ──MQTT──▶ MQTT Broker ──▶ Android App ──▶ Health Connect
```

## Download

Install the APK straight from the releases page — no need to check out and
build this repo:

**[Download `walkingpad-sync.apk`](https://github.com/klassm/android_health_connect_mqtt/releases/latest/download/walkingpad-sync.apk)**

That link always points at the APK asset of the most recent GitHub release;
a fresh build is published automatically after every push to `master`.
Android will ask you to allow installs from this source (sideloading).

## How it works

- The [macOS client](https://github.com/klassm/walkingpad_macos_client) publishes its
  WalkingPad state as a JSON MQTT message (e.g. `{"stepsTotal": 19202, ...}` on
  `homeassistant/sensor/walkingpad`).
- This app subscribes to that topic, extracts a configurable key
  (default: `stepsTotal`, a cumulative counter), computes the **delta since the
  last message**, and writes each delta as a `StepsRecord` interval into Health Connect.
- Because it works on deltas, it only counts steps that were actually walked
  while the connection was up — duplicates and state pushes are ignored.

### Delta logic

- **Duplicates** (same counter value re-delivered, QoS 1) are skipped
- **Counter resets** (treadmill reboot / day reset) are treated as a new
  baseline, no steps are fabricated
- **First data of a new day** is written as today's initial state
- **Failed Health Connect writes** are retried; the unwritten interval is
  persisted and survives app restarts
- **What Health Connect already received** (last cumulative value + time) is
  persisted; after an app restart only the missing diff is written on the same
  day, and a fresh initial state is written on a new day

## Features

- Settings screen: MQTT host, port, username, password (Keystore-encrypted), topic, JSON key, TLS toggle
- Wi-Fi gate: only syncs on a configurable Wi-Fi network (SSID selection with scan + editable field)
- Status screen: steps received via MQTT, today's steps from Health Connect, and a
  single sync state machine — `OFF / WAITING_FOR_NETWORK / CONNECTING / CONNECTED /
  DISCONNECTED` with a classified error card
- Foreground service (`dataSync` + `location`) with automatic reconnection and 30 s retry loop
- Diagnostics via logcat tag `MqttSync`

## Requirements

- Android 8.0+ (minSdk 26); Health Connect app installed on the device
- An MQTT broker reachable from the phone (e.g. Mosquitto on your Home Assistant host)
- The [WalkingPad macOS Client](https://github.com/klassm/walkingpad_macos_client)
  running on a Mac with MQTT publishing configured
  (see its [MQTT configuration docs](https://github.com/klassm/walkingpad_macos_client#mqtt-configuration))

## Setup

1. Download the APK (see [Download](#download)) and install it, open the app, and grant the requested permissions:
   - **Health Connect** — write & read steps
   - **Nearby devices** (incl. local network access) — required by Android to
     reach the broker on your LAN and to read the Wi-Fi SSID
   - **Location** (only if you use the Wi-Fi gate) — Android requires it to
     resolve the connected SSID
2. Enter broker host/port/credentials/topic and the JSON key holding the
   cumulative step counter (`stepsTotal` by default).
3. Pick your home Wi-Fi SSID.
4. Press **Start** — the status screen shows the sync state; steps appear in
   Health Connect as soon as the treadmill publishes.

## Permissions used

| Permission | Purpose |
| --- | --- |
| `POST_NOTIFICATIONS` | Foreground service notification |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` / `FOREGROUND_SERVICE_LOCATION` | Background step sync |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` / `CHANGE_NETWORK_STATE` | Wi-Fi gate + binding the socket to the Wi-Fi network |
| `NEARBY_WIFI_DEVICES` | SSID read without full location on Android 13+ |
| `ACCESS_LOCAL_NETWORK` | Android 17 local network protection — required to reach the broker |
| `READ_HEALTH_STEPS` / `WRITE_HEALTH_STEPS` | Health Connect step sync |

## Troubleshooting

- **"Not on Wi-Fi" although you are on Wi-Fi** — the app logs every gate check
  under the `MqttSync` tag; check `adb logcat -s MqttSync` for the
  `gate: ... reason=...` line.
- **TCP connect timeouts to the broker on Android 16/17** — apps targeting the
  newest SDK levels need the *local network* permission; the app handles
  granting in-app, see Settings → Apps if you revoked it.
- **Steps not appearing** — verify the macOS client publishes (e.g. with MQTT
  Explorer on the same topic/key), and that Health Connect was granted the
  permissions.

## License

Apache-2.0 — same as the
[macOS client](https://github.com/klassm/walkingpad_macos_client).
