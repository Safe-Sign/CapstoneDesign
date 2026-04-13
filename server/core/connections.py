# ─────────────────────────────────────────────────────────────
# core/connections.py — WebSocket 연결 관리
#
# 브라우저와의 WebSocket 연결 목록을 관리하고,
# 새 데이터가 수신될 때 모든 브라우저에 실시간으로 전송한다.
# ─────────────────────────────────────────────────────────────

from fastapi import WebSocket
from typing import List


class ConnectionManager:
    """
    활성화된 WebSocket 연결을 관리하는 클래스.

    브라우저가 대시보드를 열면 /ws 엔드포인트를 통해 연결되고,
    클라이언트에서 OCR 텍스트가 수신될 때마다 broadcast()로
    모든 브라우저 화면을 실시간으로 갱신한다.
    """

    def __init__(self):
        # 현재 연결된 WebSocket 목록
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        """새 브라우저 연결을 수락하고 목록에 추가"""
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        """브라우저 연결이 끊어지면 목록에서 제거"""
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, message: dict):
        """연결된 모든 브라우저에 JSON 메시지를 전송"""
        for connection in self.active_connections:
            try:
                await connection.send_json(message)
            except Exception:
                # 특정 브라우저 전송 실패 시 무시하고 나머지에게 계속 전송
                pass


# 서버 전체에서 하나의 인스턴스만 사용 (싱글톤)
manager = ConnectionManager()
