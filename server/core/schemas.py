# ─────────────────────────────────────────────────────────────
# core/schemas.py — Pydantic 요청/응답 모델
#
# 클라이언트가 서버로 보내는 데이터 형식을 정의한다.
# FastAPI가 자동으로 유효성 검사를 수행한다.
# ─────────────────────────────────────────────────────────────

from pydantic import BaseModel


class OCRRequest(BaseModel):
    """
    클라이언트가 POST /analyze 로 전송하는 요청 형식.

    필드:
    - text     : OCR로 추출된 계약서 텍스트 (민감정보 마스킹 완료)
    - filename : 원본 이미지 파일명 (선택, 기본값: "unknown")

    요청 예시:
    {
        "text": "근로계약서 내용...",
        "filename": "contract_001.jpg"
    }
    """
    text: str
    filename: str = "unknown"
    session_id: str = "unknown"
