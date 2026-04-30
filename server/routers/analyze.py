# routers/analyze.py
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from datetime import datetime
from groq import Groq
from core.database import get_db
from core.models import OCRData
from core.schemas import OCRRequest, SentenceAnalyzeRequest
from core.connections import manager
import logging
import json

logger = logging.getLogger(__name__)
router = APIRouter()

# ── 환경 변수 ─────────────────────────────────────────────────
import os
from dotenv import load_dotenv

load_dotenv()

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
if not GROQ_API_KEY:
    raise RuntimeError("GROQ_API_KEY가 설정되지 않았습니다. .env 파일을 확인하세요.")
groq_client = Groq(api_key=GROQ_API_KEY)

SYSTEM_PROMPT = """당신은 한국 노동법 전문 AI입니다. 사용자가 제출한 근로계약서 문장들을 분석하여 각 문장의 위험도를 판단하고 JSON 형식으로 결과를 출력합니다.

## 역할 및 행동 원칙
- 노무사 수준의 한국 노동법 지식을 보유합니다.
- 근로기준법, 최저임금법, 기간제법, 근로자퇴직급여 보장법을 판단 기준으로 사용합니다.
- 분석 결과는 반드시 아래 JSON 형식으로만 출력합니다. 다른 텍스트를 앞뒤에 붙이지 않습니다.

## 위험도 기준
- state 0: 위험 없음 (정상 조항)
- state 1: 저위험 (주의 필요, 법 위반 소지 낮음)
- state 2: 중위험 (법 위반 소지 있음)
- state 3: 고위험 (명백한 법 위반)

## 핵심 판단 기준
- 시간당 임금이 최저임금(2026년: 10,320원/시간) 미달 → state 3 (최저임금법 제6조)
- 주 40시간 초과 강제 → state 3 (근로기준법 제50조)
- 연장·야간·휴일 가산수당 미지급 → state 3 (근로기준법 제56조)
- 위약금·손해배상 예정 조항 → state 3 (근로기준법 제20조)
- 정당한 이유 없는 즉시해고 → state 3 (근로기준법 제23조)
- 기간제 2년 초과 → state 3 (기간제법 제4조)
- 퇴직금 포기 조항 → state 3 (근로자퇴직급여보장법 제8조)
- 수습기간 3개월 초과 → state 2
- 연차휴가 사전승인제 제한 → state 2
- 일방적 의무 부과 등 불리한 조항 → state 1

## OCR 텍스트 처리 지침
- 명백한 오탈자는 문맥으로 보정하여 분석합니다
- 판단이 불가능한 심각한 오인식은 state 1로 처리합니다

## 출력 JSON 형식
입력으로 "[id] 문장텍스트" 형태의 문장 목록이 주어집니다.
각 문장의 id를 그대로 사용하여 아래 형식으로 출력합니다.

{
  "results": [
    {"id": 1, "state": 0},
    {"id": 2, "state": 3}
  ]
}

## 처리 지침
1. 입력된 모든 문장에 대해 빠짐없이 결과를 출력합니다.
2. JSON 외의 설명 텍스트는 절대 출력하지 않습니다.
3. 입력이 근로계약서 내용이 아닌 경우: {"error": "근로계약서 내용을 입력해주세요."}를 반환합니다."""

def run_llm(text: str) -> dict:
    raw = ""
    try:
        response = groq_client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"다음 근로계약서를 분석해주세요:\n{text}"}
            ],
            temperature=0.1
        )
        raw = response.choices[0].message.content
        result = json.loads(raw)

        # LLM 응답 구조 검증
        if "results" not in result:
            raise ValueError("results 필드 없음")
        for item in result["results"]:
            if "id" not in item or "state" not in item:
                raise ValueError(f"id 또는 state 필드 없음: {item}")
            if item["state"] not in [0, 1, 2, 3]:
                raise ValueError(f"유효하지 않은 state 값: {item['state']}")

        return result
    except (json.JSONDecodeError, ValueError) as e:
        logger.error("LLM 응답 검증 실패 (%s): %s", str(e), raw)
        raise HTTPException(status_code=500, detail="AI 분석 결과 파싱 실패")
    except Exception as e:
        logger.error("LLM 호출 실패: %s", str(e))
        raise HTTPException(status_code=502, detail="AI 분석 중 오류가 발생했습니다.")


@router.post("/analyze")
async def analyze(ocr_request: OCRRequest, db: Session = Depends(get_db)):
    if not ocr_request.text.strip():
        raise HTTPException(status_code=400, detail="텍스트가 비어있습니다.")

    logger.info("분석 요청 수신 - session: %s, filename: %s", ocr_request.session_id, ocr_request.filename)

    llm_result = run_llm(ocr_request.text)

    entry = OCRData(
        session_id=ocr_request.session_id,
        filename=ocr_request.filename,
        text=ocr_request.text,
        timestamp=datetime.now(),
        status="분석 완료",
        llm_result=json.dumps(llm_result, ensure_ascii=False)
    )
    db.add(entry)
    db.commit()
    db.refresh(entry)

    await manager.broadcast({
        "id": entry.id,
        "session_id": entry.session_id,
        "filename": entry.filename,
        "text": entry.text,
        "timestamp": entry.timestamp,
        "status": entry.status,
        "llm_result": llm_result
    })

    logger.info("분석 완료 - id: %d", entry.id)
    return {"status": "success", "id": entry.id, "llm_result": llm_result}


@router.get("/data")
async def get_data(db: Session = Depends(get_db)):
    data = db.query(OCRData).order_by(OCRData.id.desc()).all()
    items = [
        {
            "id": d.id,
            "session_id": d.session_id,
            "filename": d.filename,
            "timestamp": d.timestamp,
            "status": d.status,
            "llm_result": d.llm_result
        }
        for d in data
    ]
    return {"count": len(items), "items": items}


@router.post("/analyze/sentences")
async def analyze_sentences(req: SentenceAnalyzeRequest, db: Session = Depends(get_db)):
    if not req.sentences:
        raise HTTPException(status_code=400, detail="sentences가 비어있습니다.")

    logger.info("문장 분석 요청 - session: %s, 문장 수: %d", req.session_id, len(req.sentences))

    combined_text = "\n".join(f"[{s.id}] {s.text}" for s in req.sentences)
    llm_result = run_llm(combined_text)

    entry = OCRData(
        session_id=req.session_id,
        filename=req.filename,
        text=combined_text,
        timestamp=datetime.now(),
        status="분석 완료",
        llm_result=json.dumps(llm_result, ensure_ascii=False)
    )
    db.add(entry)
    db.commit()
    db.refresh(entry)

    await manager.broadcast({
        "id": entry.id,
        "session_id": entry.session_id,
        "filename": entry.filename,
        "timestamp": entry.timestamp,
        "status": entry.status,
        "llm_result": llm_result
    })

    logger.info("문장 분석 완료 - id: %d", entry.id)
    return {"status": "success", "id": entry.id, "llm_result": llm_result}