# ─────────────────────────────────────────────────────────────
# main.py — FastAPI 앱 시작점
#
# 역할:
#   - FastAPI 앱 인스턴스 생성
#   - DB 테이블 초기화
#   - 라우터 등록
#   - 대시보드(/) 및 WebSocket(/ws) 엔드포인트 정의
# ─────────────────────────────────────────────────────────────

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request, Depends
from fastapi.templating import Jinja2Templates
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from pathlib import Path
import logging
import os
from dotenv import load_dotenv

load_dotenv()

from core.database import engine, get_db, Base
from core.models import OCRData
from core.connections import manager
from routers import analyze

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)

# 현재 파일(main.py) 기준으로 templates 폴더 절대 경로 설정
BASE_DIR = Path(__file__).parent

# ── 앱 초기화 ────────────────────────────────────────────────

app = FastAPI(title="근로계약서 분석 서버")

_raw_origins = os.getenv("ALLOWED_ORIGINS", "*")
_origins = ["*"] if _raw_origins == "*" else [o.strip() for o in _raw_origins.split(",")]

app.add_middleware(
    CORSMiddleware,
    allow_origins=_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Jinja2 템플릿 엔진 (대시보드 HTML 렌더링)
templates = Jinja2Templates(directory=str(BASE_DIR / "templates"))

# 서버 시작 시 DB 테이블이 없으면 자동 생성
Base.metadata.create_all(bind=engine)

# ── 라우터 등록 ──────────────────────────────────────────────

# /analyze, /data 엔드포인트를 routers/analyze.py 에서 가져옴
app.include_router(analyze.router)


# ── 헬스체크 ─────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok"}


# ── 대시보드 ─────────────────────────────────────────────────

@app.get("/", response_class=HTMLResponse)
async def index(request: Request, db: Session = Depends(get_db)):
    """
    대시보드 페이지를 렌더링해서 반환.
    DB에 저장된 모든 OCR 텍스트 목록을 최신순으로 불러와서 전달한다.
    """
    data = db.query(OCRData).order_by(OCRData.id.desc()).all()
    return templates.TemplateResponse(request, "index.html", {
        "data": data
    })


# ── WebSocket ────────────────────────────────────────────────

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """
    브라우저와 WebSocket 연결을 유지하는 엔드포인트.
    브라우저가 대시보드를 열면 자동으로 연결되고,
    클라이언트에서 데이터가 들어올 때마다 실시간으로 화면이 갱신된다.
    """
    await manager.connect(websocket)
    try:
        while True:
            # 브라우저로부터 메시지를 대기 (연결 유지 목적)
            await websocket.receive_text()
    except WebSocketDisconnect:
        # 브라우저 탭을 닫거나 연결이 끊어지면 목록에서 제거
        manager.disconnect(websocket)