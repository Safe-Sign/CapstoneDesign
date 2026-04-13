# ─────────────────────────────────────────────────────────────
# routers/analyze.py — /analyze, /data 라우트
#
# 클라이언트에서 OCR 텍스트를 수신하고 DB에 저장하는 엔드포인트.
# 추후 LLM 분석 로직이 이 파일에 추가될 예정.
# ─────────────────────────────────────────────────────────────

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from datetime import datetime

from core.database import get_db
from core.models import OCRData
from core.schemas import OCRRequest
from core.connections import manager

# 이 파일의 라우트들은 main.py에서 prefix 없이 등록됨
router = APIRouter()


@router.post("/analyze")
async def analyze(ocr_request: OCRRequest, db: Session = Depends(get_db)):
    """
    클라이언트(모바일 앱)에서 OCR 텍스트를 전송하는 엔드포인트.

    처리 순서:
    1. 수신된 텍스트를 DB에 저장
    2. 연결된 브라우저에 WebSocket으로 실시간 전송
    3. 수신 완료 응답 반환

    추후 이 함수 내부에 LLM 분석 로직이 추가될 예정.
    """
    # DB에 새 레코드 저장
    entry = OCRData(
        session_id=ocr_request.session_id,
        filename=ocr_request.filename,
        text=ocr_request.text,
        timestamp=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        status="수신 완료"
    )
    db.add(entry)
    db.commit()
    db.refresh(entry)  # DB에서 자동 생성된 id 값을 가져옴

    # WebSocket으로 브라우저에 실시간 전송할 데이터
    broadcast_data = {
        "id": entry.id,
        "session_id": entry.session_id,
        "filename": entry.filename,
        "text": entry.text,
        "timestamp": entry.timestamp,
        "status": entry.status
    }
    await manager.broadcast(broadcast_data)

    return {
        "status": "success",
        "message": "텍스트 수신 완료",
        "id": entry.id
    }


@router.get("/data")
async def get_data(db: Session = Depends(get_db)):
    """
    현재까지 수신된 모든 OCR 텍스트 데이터를 JSON으로 반환.
    디버깅 또는 클라이언트 조회용.
    """
    data = db.query(OCRData).order_by(OCRData.id.desc()).all()
    items = [
        {
            "id": d.id,
            "filename": d.filename,
            "text": d.text,
            "timestamp": d.timestamp,
            "status": d.status
        }
        for d in data
    ]
    return {"count": len(items), "items": items}