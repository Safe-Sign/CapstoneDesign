import requests

BASE = "http://localhost:8000"

def test_health():
    r = requests.get(f"{BASE}/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"
    print("PASS /health")

def test_analyze_sentences():
    r = requests.post(f"{BASE}/analyze/sentences", json={
        "session_id": "test",
        "filename": "test.jpg",
        "sentences": [
            {"id": 1, "text": "근무시간은 주 6일 1일 12시간으로 한다."},
            {"id": 2, "text": "연장근로수당은 지급하지 않는다."},
            {"id": 3, "text": "근로계약 기간은 1년으로 한다."}
        ]
    })
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "success"
    results = body["llm_result"]["results"]
    assert len(results) == 3
    ids = [x["id"] for x in results]
    assert sorted(ids) == [1, 2, 3]
    for x in results:
        assert x["state"] in [0, 1, 2, 3]
    print("PASS /analyze/sentences")

def test_empty_sentences():
    r = requests.post(f"{BASE}/analyze/sentences", json={
        "sentences": []
    })
    assert r.status_code == 400
    print("PASS 빈 sentences → 400 에러")

def test_invalid_body():
    r = requests.post(f"{BASE}/analyze/sentences", json={})
    assert r.status_code == 422
    print("PASS 잘못된 요청 → 422 에러")

if __name__ == "__main__":
    test_health()
    test_analyze_sentences()
    test_empty_sentences()
    test_invalid_body()
    print("\n모든 테스트 통과")
