# 개인정보 보호형 LLM 기반 실시간 근로계약서 분석 시스템

OCR로 근로계약서를 읽고, 문장별로 독소조항 위험도를 분석해 반환하는 서버입니다.

## 기술 스택

- **FastAPI** — REST API 서버
- **Groq (LLaMA 3.3 70B)** — 독소조항 분석 LLM
- **SQLAlchemy + SQLite** — 분석 기록 저장
- **WebSocket** — 실시간 결과 브로드캐스트

## 폴더 구조

```
server/
├── main.py          # FastAPI 앱 시작점, WebSocket, 헬스체크
├── requirements.txt
├── .env             # API 키 설정 (git 제외)
├── core/
│   ├── database.py  # DB 연결
│   ├── models.py    # DB 테이블 모델
│   ├── schemas.py   # 요청/응답 Pydantic 모델
│   └── connections.py # WebSocket 연결 관리
├── routers/
│   └── analyze.py   # /analyze, /analyze/sentences, /data 엔드포인트
└── templates/
    └── index.html   # 대시보드
```

## 실행 방법

**1. 패키지 설치**
```bash
cd server
pip install -r requirements.txt
```

**2. 환경변수 설정**
```bash
cp .env.example .env
# .env 파일에 GROQ_API_KEY 입력
```

**3. 서버 실행**
```bash
uvicorn main:app --reload
```

**4. API 문서 확인**

`http://localhost:8000/docs`

## API 명세

### GET /health
서버 상태 확인
```json
{ "status": "ok" }
```

### POST /analyze/sentences
문장별 독소조항 분석 (메인 엔드포인트)

**요청**
```json
{
  "session_id": "abc123",
  "filename": "contract.jpg",
  "sentences": [
    { "id": 1, "text": "근무시간은 주 6일 1일 12시간으로 한다." },
    { "id": 2, "text": "연장근로수당은 지급하지 않는다." }
  ]
}
```

**응답**
```json
{
  "status": "success",
  "id": 1,
  "llm_result": {
    "results": [
      { "id": 1, "state": 3 },
      { "id": 2, "state": 3 }
    ]
  }
}
```

**state 값**
| state | 의미 |
|-------|------|
| 0 | 정상 |
| 1 | 저위험 |
| 2 | 중위험 |
| 3 | 고위험 (법 위반) |

### GET /data
분석 기록 전체 조회

### WebSocket /ws
분석 완료 시 실시간 결과 수신
