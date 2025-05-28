#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>

const int VIBRATE_PIN   = 14;  // 진동 출력 핀
const int ANALOG_PIN    = 4;   // 센서 보드 Analog Out → ESP32 GPIO4

// BLE 서비스 및 특성 UUID
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_UUID_TASK      "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// Task 특성 쓰기 콜백
class TaskCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    String val = pChar->getValue();
    int taskId = val.toInt();
    digitalWrite(VIBRATE_PIN, taskId == 2 ? HIGH : LOW);
  }
};

// 연결/끊김 콜백
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {}
  void onDisconnect(BLEServer* pServer) override {
    pServer->startAdvertising();
  }
};

void setup() {
  Serial.begin(115200);

  pinMode(VIBRATE_PIN, OUTPUT);
  digitalWrite(VIBRATE_PIN, LOW);

  pinMode(ANALOG_PIN, INPUT);        // ← 아날로그 입력으로 설정

  // (필요하다면 감쇠 설정—ESP32 ADC2 채널은 기본 11dB 감쇠)
  // analogSetPinAttenuation(ANALOG_PIN, ADC_11db);

  // BLE 초기화
  BLEDevice::init("ESP32_TREMOR");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  BLECharacteristic *taskChar = pService->createCharacteristic(
    CHAR_UUID_TASK,
    BLECharacteristic::PROPERTY_WRITE
  );
  taskChar->setCallbacks(new TaskCallbacks());

  pService->start();
  BLEAdvertising *pAdv = pServer->getAdvertising();
  pAdv->addServiceUUID(SERVICE_UUID);
  pAdv->start();

  Serial.println("BLE GATT 서버 시작: ESP32_TREMOR");
}

void loop() {
  // 1) 아날로그 값 읽기
  int raw = analogRead(ANALOG_PIN);           // 0 ~ 4095 (12-bit)z
  float voltage = raw * (3.3f / 4095.0f);     // 실제 전압 환산

  // 2) 시리얼로 출력 (디버깅 용도)
  Serial.print("Analog[4]: ");
  Serial.print(raw);
  Serial.print(" (");
  Serial.print(voltage, 3);
  Serial.println(" V)");

  delay(500);
}
