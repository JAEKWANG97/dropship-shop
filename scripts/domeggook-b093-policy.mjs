import assert from "node:assert/strict";

export const DEFERRED_CATEGORY_CODES = new Set(["PPE_WORK_GLOVES"]);

export const REFERENCE_ITEM_NUMBERS = ["44092831", "43675480", "33832643", "8667274", "57723620"];

const define = (collectionPolicy, keywords = []) => ({ collectionPolicy, keywords });

export const B093_CATEGORY_POLICY = {
  PPE_SAFETY_HELMET: define("A", ["산업용 안전모", "건설용 안전모", "KCS 안전모"]),
  PPE_SAFETY_SHOES: define("A", ["산업용 안전화", "건설 안전화", "경량 안전화", "발목 안전화"]),
  PPE_FALL_ARREST_HARNESS: define("A", ["전체식 안전대", "전신 안전대", "전신 하네스", "그네식 안전대"]),
  PPE_SAFETY_BELT: define("A", ["주상용 안전대", "벨트식 안전대", "허리식 안전대", "안전대 보조벨트"]),
  PPE_SAFETY_GLASSES: define("A", ["산업용 보안경", "작업용 보호안경", "안전고글"]),
  PPE_RESPIRATOR: define("A", ["산업용 방진마스크", "1급 방진마스크", "2급 방진마스크", "반면형 방독마스크"]),
  PPE_EAR_PROTECTION: define("A", ["산업용 귀마개", "산업용 귀덮개", "청력보호구", "방음 귀덮개"]),
  PPE_INSULATED_GLOVES: define("A", ["절연장갑", "전기 절연장갑", "고무 절연장갑"]),
  PPE_WELDING_GLOVES: define("A", ["용접장갑", "알곤장갑", "내열 용접장갑", "TIG 장갑"]),
  PPE_HIGH_VISIBILITY_VEST: define("A", ["작업용 안전조끼", "공사현장 반사조끼", "신호수 조끼", "망사 안전조끼"]),
  PPE_PROTECTIVE_CLOTHING: define("A", ["산업용 보호복", "화학 방호복", "분진 보호복", "일회용 작업복"]),
  FALL_PREVENTION_NET: define("A", ["건설 추락방지망", "추락방지 안전망", "수직 보호망"]),
  FALLING_OBJECT_NET: define("A", ["낙하물방지망", "낙하물망", "건설 낙하방지망"]),
  OPENING_COVER: define("A", ["개구부덮개", "개구부 안전덮개", "원형 개구부덮개"]),
  WORK_PLATFORM: define("A", ["작업발판", "말비계", "산업용 우마", "고소작업 발판"]),
  SAFETY_BLOCK: define("A", ["추락방지 안전블록", "자동감김 안전블록", "안전블럭"]),
  SAFETY_SIGN: define("A", ["산업안전표지판", "공사중 표지판", "작업중 입간판", "안전주의 표지판"]),
  WARNING_SIGN: define("A", ["위험표지판", "추락주의 표지판", "감전주의 표지판", "출입금지 표지판"]),
  TRAFFIC_CONE: define("A", ["라바콘", "칼라콘", "안전콘", "접이식 라바콘"]),
  SAFETY_FENCE: define("A", ["이동식 안전휀스", "공사장 안전펜스", "접이식 안전휀스"]),
  BARRICADE: define("A", ["이동식 바리케이드", "접이식 바리케이드", "스틸 바리케이드", "플라스틱 바리케이드"]),
  WARNING_LIGHT: define("A", ["산업용 경광등", "공사장 점멸등", "태양광 경고등", "칼라콘 경고등"]),
  SIGNAL_BATON: define("A", ["교통 신호봉", "LED 신호봉", "안전 지시봉", "주차 유도봉"]),
  BARRIER_TAPE: define("A", ["출입금지 테이프", "접근금지 테이프", "위험 테이프", "폴리스라인"]),
  GAS_DETECTOR: define("A", ["휴대용 가스검지기", "가스누설검지기", "복합가스측정기", "가연성가스 검지기"]),
  OXYGEN_METER: define("A", ["휴대용 산소측정기", "산소농도측정기", "산소 검지기", "O2 측정기"]),
  NOISE_METER: define("A", ["산업용 소음계", "디지털 소음측정기", "데시벨측정기"]),
  LIGHT_METER: define("A", ["디지털 조도계", "휴대용 조도측정기", "LUX 측정기"]),
  ANEMOMETER: define("A", ["디지털 풍속계", "휴대용 풍속측정기", "열선 풍속계", "풍량계"]),
  VIBRATION_METER: define("A", ["디지털 진동계", "설비 진동측정기", "베어링 진동계"]),
  THERMAL_CAMERA_INSPECTION: define("A", ["산업용 열화상카메라", "휴대용 열화상카메라", "열화상 측정기", "적외선 열화상카메라"]),
  FIRST_AID_KIT: define("A", ["산업용 구급함", "구급가방", "응급처치키트", "구급상자 세트"]),
  FIRST_AID_SUPPLIES: define("A", ["응급처치세트", "구급용품세트", "붕대 구급세트", "산업용 응급키트"]),

  FALL_PREVENTION_GUARDRAIL: define("R", ["이동식 안전난간", "공사장 안전난간", "철제 안전난간"]),
  SAFE_PASSAGE: define("R", ["가설통로 발판", "통로발판", "비계 발판"]),
  LIFELINE: define("R", ["수직 생명줄", "수평 생명줄", "안전대 생명줄", "로프그랩 세트"]),
  DUST_METER: define("R", ["산업용 분진측정기", "휴대용 미세먼지측정기", "입자측정기", "PM2.5 측정기"]),
  AED: define("R", ["자동심장충격기", "AED", "제세동기"]),
  EYEWASH_STATION: define("R", ["비상세안기", "응급세안기", "산업용 눈세척기", "아이워시 스테이션"]),
  HEAT_COLD_PREVENTION_SUPPLIES: define("R", ["작업용 냉감조끼", "산업용 아이스조끼", "폭염 예방 키트", "혹한기 작업용품"]),
  VENTILATION_EQUIPMENT: define("R", ["산업용 송풍기", "이동식 송풍기", "덕트 송풍기", "방폭 송풍기"]),
  SMART_SAFETY_HELMET: define("R", ["스마트 안전모", "IoT 안전모", "위치추적 안전모", "통신 안전모"]),
  SMART_SAFETY_VEST: define("R", ["스마트 안전조끼", "IoT 안전조끼", "작업자 감지 안전조끼"]),
  SMART_SAFETY_HARNESS: define("R", ["스마트 안전대", "IoT 안전대", "추락감지 안전대"]),
  SMART_WATCH: define("R", ["산업안전 스마트워치", "SOS 스마트워치", "낙상감지 스마트워치"]),
  SMART_CCTV_MOBILE: define("R", ["공사현장 이동식 CCTV", "이동식 CCTV", "포터블 CCTV"]),
  SMART_CCTV_SOLAR_MOBILE: define("R", ["태양광 CCTV", "무선 태양광 CCTV", "태양광 이동식 CCTV"]),
  SMART_CCTV_PTZ: define("R", ["산업용 PTZ CCTV", "회전형 CCTV", "광학줌 CCTV"]),
  SMART_CCTV_THERMAL: define("R", ["열화상 CCTV", "열감지 CCTV", "열화상 네트워크 카메라"]),
  SMART_CCTV_DUAL_SPECTRUM: define("R", ["듀얼스펙트럼 열화상 카메라", "열화상 광학 CCTV"]),
  WORKER_LOCATION_TRACKING: define("R", ["UWB 위치추적 태그", "BLE 비콘 태그", "RFID 작업자 태그", "작업자 위치추적 단말"]),
  WORKER_ACCESS_CONTROL: define("R", ["출입통제 단말기", "RFID 출입통제", "카드 출입통제", "얼굴인식 출입통제"]),
  WORKER_SOS_EMERGENCY_CALL: define("R", ["산업용 비상호출기", "SOS 호출벨", "무선 비상벨", "비상호출벨"]),
  HEAVY_EQUIPMENT_PROXIMITY_ALARM: define("R", ["지게차 접근경보기", "중장비 접근경보", "작업자 감지 경보기"]),
  HEAVY_EQUIPMENT_COLLISION_PREVENTION: define("R", ["지게차 충돌방지장치", "중장비 충돌방지", "장비 접근감지 센서"]),
  HEAVY_EQUIPMENT_REAR_DETECTOR: define("R", ["지게차 후방감지기", "중장비 후방카메라", "백부저", "후방센서"]),
  HEAVY_EQUIPMENT_PINCH_PREVENTION: define("R", ["지게차 협착방지장치", "중장비 협착방지", "작업자 감지센서"]),
  CRANE_PROXIMITY_ALARM: define("R", ["크레인 접근경보기", "타워크레인 충돌방지", "크레인 경보장치"]),
  OPENING_PROXIMITY_ALARM: define("R", ["개구부 접근경보기", "개구부 경보장치", "추락위험 경보기"]),
  FALL_DETECTION_SYSTEM: define("R", ["추락감지센서", "추락감지 경보기", "산업용 낙상감지"]),
  SCAFFOLD_DISPLACEMENT_MONITORING: define("R", ["비계 변위센서", "구조물 변위계", "무선 변위센서"]),
  RETAINING_WALL_MEASUREMENT_SYSTEM: define("R", ["흙막이 계측기", "지중경사계", "구조물 하중계"]),
  IOT_GAS_DETECTOR: define("R", ["IoT 가스감지기", "무선 가스검지기", "LoRa 가스센서"]),
  IOT_DUST_METER: define("R", ["IoT 미세먼지측정기", "무선 분진측정기", "LoRa 분진센서"]),
  IOT_NOISE_METER: define("R", ["IoT 소음측정기", "무선 소음계", "환경소음 모니터링"]),
  IOT_TEMPERATURE_HUMIDITY_METER: define("R", ["IoT 온습도계", "무선 온습도 센서", "LoRa 온습도계", "온습도 데이터로거"]),
  IOT_ANEMOMETER: define("R", ["IoT 풍속계", "무선 풍속센서", "LoRa 풍속계"]),

  DANGER_AREA_BARRIER: define("M"),
  ACCESS_CONTROL_FACILITY: define("M"),
  SMART_GAS_DETECTOR: define("M"),
  SMART_CCTV_AI_VIDEO_ANALYTICS: define("M"),
  SMART_CCTV_AI_SAFETY_MANAGEMENT: define("M"),
  SMART_CCTV_AI_HELMET_DETECTION: define("M"),
  SMART_CCTV_AI_VEST_DETECTION: define("M"),
  SMART_CCTV_AI_DANGER_ZONE_INTRUSION: define("M"),
  SMART_CCTV_AI_FALL_RISK_DETECTION: define("M"),
  SMART_CCTV_AI_FALLEN_WORKER_DETECTION: define("M"),
  SMART_CCTV_AI_FIRE_SMOKE_DETECTION: define("M"),
  SMART_CCTV_GENERAL_SPECIAL: define("M"),
  WORKER_LOCATION_ACCESS_MANAGEMENT: define("M"),
  WORKER_ELECTRONIC_ACCESS_CONTROL: define("M"),
};

export function policyForCategory(code) {
  return B093_CATEGORY_POLICY[code] || null;
}

export function assertB093Policy(categoryCodes) {
  const targetCodes = categoryCodes.filter((code) => !DEFERRED_CATEGORY_CODES.has(code));
  const policyCodes = Object.keys(B093_CATEGORY_POLICY);
  const missing = targetCodes.filter((code) => !B093_CATEGORY_POLICY[code]);
  const unknown = policyCodes.filter((code) => !targetCodes.includes(code));
  const duplicateKeywords = policyCodes.filter((code) => {
    const policy = B093_CATEGORY_POLICY[code];
    return policy.collectionPolicy !== "M" && new Set(policy.keywords).size !== policy.keywords.length;
  });
  assert.equal(targetCodes.length, 81, `B-093 대상 카테고리는 81개여야 합니다: ${targetCodes.length}`);
  assert.equal(policyCodes.length, 81, `B-093 정책 카테고리는 81개여야 합니다: ${policyCodes.length}`);
  assert.deepEqual(missing, [], `B-093 정책 누락 카테고리: ${missing.join(", ")}`);
  assert.deepEqual(unknown, [], `B-093 정책에만 있는 카테고리: ${unknown.join(", ")}`);
  assert.deepEqual(duplicateKeywords, [], `중복 검색어 카테고리: ${duplicateKeywords.join(", ")}`);
  for (const code of policyCodes) {
    const policy = B093_CATEGORY_POLICY[code];
    assert.ok(["A", "R", "M"].includes(policy.collectionPolicy), `${code} policy 값 오류`);
    assert.equal(policy.collectionPolicy === "M", policy.keywords.length === 0, `${code} 검색어 정책 오류`);
  }
}
