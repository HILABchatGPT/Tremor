#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>

const int VIBRATE_PIN = 14;  // 진동 모터 제어 핀
const int BUTTON_PIN  = 12;  // 버튼 입력 핀
const int ANALOG_PIN  = 4;   // Tremor 센서 아날로그 출력 핀

// BLE 서비스 및 특성 UUID
#define SERVICE_UUID   "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_UUID_TASK "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// BLE를 통해 받은 명령으로 인한 진동 상태
volatile bool bleVibrate = false;

class TaskCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    String val = pChar->getValue();
    int taskId = val.toInt();
    bleVibrate = (taskId == 2); // 값이 2면 진동 활성화
  }
};

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {}
  void onDisconnect(BLEServer* pServer) override { pServer->startAdvertising(); }
};

void setup() {
  Serial.begin(115200);

  pinMode(VIBRATE_PIN, OUTPUT);
  digitalWrite(VIBRATE_PIN, LOW);

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(ANALOG_PIN, INPUT);

  BLEDevice::init("ESP32_TREMOR");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  BLECharacteristic *taskChar = pService->createCharacteristic(
      CHAR_UUID_TASK, BLECharacteristic::PROPERTY_WRITE);
  taskChar->setCallbacks(new TaskCallbacks());

  pService->start();
  BLEAdvertising *pAdv = pServer->getAdvertising();
  pAdv->addServiceUUID(SERVICE_UUID);
  pAdv->start();

  Serial.println("BLE GATT 서버 시작: ESP32_TREMOR");
}

void loop() {
  bool pressed = digitalRead(BUTTON_PIN) == LOW;

  // 500ms마다 아날로그 값 출력
  static unsigned long lastPrint = 0;
  unsigned long now = millis();
  if (now - lastPrint >= 500) {
    int raw = analogRead(ANALOG_PIN);
    float voltage = raw * (3.3f / 4095.0f);
    Serial.print("Analog[4]: ");
    Serial.print(raw);
    Serial.print(" (");
    Serial.print(voltage, 3);
    Serial.println(" V)");
    lastPrint = now;
  }

  // BLE 신호 또는 버튼 입력이 있으면 진동
  if (bleVibrate || pressed) {
    digitalWrite(VIBRATE_PIN, HIGH);
  } else {
    digitalWrite(VIBRATE_PIN, LOW);
  }

  delay(20); // 짧은 지연으로 버튼 디바운스
}

