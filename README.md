# Tremor

This repository contains a set of Arduino sketches for an ESP32-S3 board that
reads a tremor sensor and controls a vibration motor. Bluetooth Low Energy (BLE)
can be used to start the vibration from an external device.

## Hardware connections

| Purpose                     | ESP32-S3 pin | Notes                           |
|-----------------------------|--------------|---------------------------------|
| Vibration motor             | GPIO14       | Drive via transistor/MOSFET     |
| Push button                 | GPIO12       | Uses internal pull-up resistor  |
| Tremor sensor analog output | GPIO4        | 0-3.3&nbsp;V analog input        |

The board should be powered from 3.3&nbsp;V and GND with the sensor connected to
`GPIO4`.

## Building and flashing (ESP32-S3)

1. Install **Arduino IDE** or `arduino-cli` with the Espressif board definitions
   (`esp32` core).
2. Select **`ESP32S3 Dev Module`** as the board.
3. Open any of the sketches in this repository.
4. Compile and upload to the board using the IDE or:

   ```bash
   arduino-cli compile --fqbn esp32:esp32:esp32s3 sketch_may13a.ino
   arduino-cli upload  --fqbn esp32:esp32:esp32s3 -p /dev/ttyUSB0 sketch_may13a.ino
   ```

Adjust the serial port as required.

## BLE UUIDs

All sketches use the same BLE service and characteristic UUIDs:

```
Service UUID:        4fafc201-1fb5-459e-8fcc-c5c9c331914b
Characteristic UUID: beb5483e-36e1-4688-b7f5-ea07361b26a8
```

Writing the ASCII string `"2"` to the characteristic activates the vibration
motor. Any other value (e.g. `"0"`) stops it.

Example using `gatttool`:

```bash
gatttool -b <MAC> --char-write-req \
  -a 0x000e -n 32   # ASCII code for "2"
```

Replace `<MAC>` with the advertised address of the ESP32-S3.

## Sketch overview

### `sketch_may13a.ino`
Reads the tremor sensor on `GPIO4` and prints the value to the serial monitor
every 500&nbsp;ms. BLE writes to the `Task` characteristic control the vibration
motor.

### `sketch_may27a.ino`
Demonstrates controlling the vibration motor with a push button on `GPIO12`.
BLE is not used in this sketch.

### `sketch_combined.ino`
Combines the features of the previous sketches: sensor reading, button input and
BLE-controlled vibration.

## Android prototype

The `android/` folder contains a minimal Kotlin/Compose activity demonstrating
how to connect to the ESP32 over BLE. The screen displays a simple target while
logging touch and drag coordinates to the app's private storage.

