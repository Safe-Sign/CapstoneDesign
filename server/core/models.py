# ─────────────────────────────────────────────────────────────
# core/models.py — SQLAlchemy DB 테이블 모델
#
# DB에 실제로 생성될 테이블 구조를 정의한다.
# ─────────────────────────────────────────────────────────────

from sqlalchemy import Column, Integer, String, Text, DateTime
from datetime import datetime
from .database import Base


class OCRData(Base):
    """
    수신된 OCR 텍스트를 저장하는 테이블 (ocr_data).

    컬럼:
    - id        : 자동 증가 고유 번호
    - filename  : 원본 이미지 파일명
    - text      : OCR로 추출된 계약서 텍스트 (민감정보 마스킹 완료)
    - timestamp : 서버 수신 시각
    - status    : 처리 상태 (수신 완료 / 분석 중 / 분석 완료 등)

    추후 추가 예정 컬럼:
    - risk_level  : LLM 분석 위험 등급 (HIGH / MEDIUM / LOW)
    - analysis    : LLM 분석 결과 JSON
    """
    __tablename__ = "ocr_data"

    id = Column(Integer, primary_key=True, index=True)  # 자동 증가 ID
    session_id = Column(String, default="unknown")       # 클라이언트 세션 ID
    filename = Column(String, default="unknown")         # 원본 이미지 파일명
    text = Column(Text, nullable=False)                  # OCR 추출 텍스트
    timestamp = Column(DateTime, default=datetime.now)   # 수신 시각
    status = Column(String, default="수신 완료")         # 처리 상태
    llm_result = Column(Text, nullable=True)