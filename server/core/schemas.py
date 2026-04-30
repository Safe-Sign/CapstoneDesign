# ─────────────────────────────────────────────────────────────
# core/schemas.py — Pydantic 요청/응답 모델
#
# 클라이언트가 서버로 보내는 데이터 형식을 정의한다.
# FastAPI가 자동으로 유효성 검사를 수행한다.
# ─────────────────────────────────────────────────────────────

from pydantic import BaseModel, Field
from typing import List, Optional


class OCRRequest(BaseModel):
    """
    클라이언트가 POST /analyze 로 전송하는 요청 형식.

    필드:
    - text     : OCR로 추출된 계약서 텍스트 (클라이언트에서 민감정보 마스킹 완료 후 전송)
    - filename : 원본 이미지 파일명 (선택, 기본값: "unknown")

    요청 예시:
    {
        "text": "근로계약서 내용...",
        "filename": "contract_001.jpg"
    }
    """
    text: str = Field(..., min_length=1, max_length=20000)
    filename: str = "unknown"
    session_id: str = "unknown"


class Sentence(BaseModel):
    id: int
    text: str = Field(..., min_length=1, max_length=1000)


class SentenceAnalyzeRequest(BaseModel):
    """
    클라이언트가 POST /analyze/sentences 로 전송하는 요청 형식.

    요청 예시:
    {
        "session_id": "abc123",
        "filename": "contract.jpg",
        "sentences": [
            {"id": 1, "text": "근로 기간은 1년으로 한다."},
            {"id": 2, "text": "연장근로수당은 지급하지 않는다."}
        ]
    }
    """
    session_id: str = "unknown"
    filename: str = "unknown"
    sentences: List[Sentence]
