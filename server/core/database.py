# ─────────────────────────────────────────────────────────────
# core/database.py — DB 연결 설정
#
# SQLite DB 엔진, 세션, Base 클래스를 생성한다.
# 다른 파일에서 이 모듈을 import해서 사용한다.
# ─────────────────────────────────────────────────────────────

from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker
from pathlib import Path

# DB 파일 경로 (server/ocr_data.db)
BASE_DIR = Path(__file__).parent.parent
DATABASE_URL = f"sqlite:///{BASE_DIR / 'ocr_data.db'}"

# DB 엔진 생성
# check_same_thread=False: FastAPI의 비동기 환경에서 SQLite 사용 시 필요
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})

# DB 세션 팩토리
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# 모든 모델의 부모 클래스
Base = declarative_base()


def get_db():
    """
    FastAPI 의존성 함수.
    요청마다 DB 세션을 생성하고, 요청이 끝나면 자동으로 닫는다.
    """
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
