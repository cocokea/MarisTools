# MarisTools

Plugin Bukkit/Folia dùng Gradle để build.

## Build

Yêu cầu Java 25 và có mạng để script `gradlew` tải Gradle 9.1.0 lần đầu:

```bash
./gradlew build
```

## Dependency chính

- `io.papermc.paper:paper-api:26.1.2.build.+)

## Files cấu hình

- `config.yml`
- `message.yml`
- `tools.yml`

## Lệnh

- `/tools give <player> <tool> <duration>`
- `/tools reload`

## Ghi chú triển khai

- Dùng NBT để lưu `tool id`, `duration`, `expire runtime`.
- Timer dựa trên **server runtime** nên khi server tắt thì thời gian sẽ pause.
- Rocket lore được refresh mỗi 5 phút.
- Drill phá 3x3 theo **mặt phẳng ngang**.
