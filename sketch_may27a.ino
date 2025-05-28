#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>

const int VIBRATE_PIN = 14;  // 진동 모터 제어 핀
const int BUTTON_PIN  = 12;  // 버튼 입력 핀

void setup() {
  Serial.begin(115200);
  
  // 진동 모터
  pinMode(VIBRATE_PIN, OUTPUT);
  digitalWrite(VIBRATE_PIN, LOW);

  // 버튼 (내부 풀업)
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  // BLE 초기화 (기존 코드 유지)
  BLEDevice::init("ESP32_TREMOR");
  BLEServer *pServer = BLEDevice::createServer();
  // … (이어서 기존 BLE 설정)
}

void loop() {
  // 1) 버튼 상태 읽기 (눌렀을 때 LOW)
  bool pressed = (digitalRead(BUTTON_PIN) == LOW);

  // 2) 진동 모터 제어
  if (pressed) {
    digitalWrite(VIBRATE_PIN, HIGH);
  } else {
    digitalWrite(VIBRATE_PIN, LOW);
  }

  // (원한다면 BLE Task 콜백과 결합해서, BLE 신호·버튼 조합으로 진동 패턴을 다양화할 수도 있습니다.)


  delay(20);  // 소프트웨어 디바운싱 겸
}
