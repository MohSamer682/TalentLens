from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import sys


SCORE_JSON = {
    "technical_skills": 93,
    "experience": 84,
    "education": 72,
    "soft_skills": 66,
    "culture_fit": 81,
    "skills": ["Java", "Spring Boot", "PostgreSQL"],
    "justification": "Deterministic fake LLM score for end-to-end verification.",
    "strengths": "Backend API and database experience.",
    "gaps": "No production provider was called in this smoke test."
}


class FakeLlmHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_json(404, {"error": "not found"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            request = json.loads(self.rfile.read(content_length))
        except Exception as exc:
            self.send_json(400, {"error": f"invalid json: {exc}"})
            return

        if request.get("response_format", {}).get("type") != "json_object":
            self.send_json(400, {"error": "response_format.type=json_object required"})
            return

        self.send_json(200, {
            "choices": [{
                "message": {
                    "content": json.dumps(SCORE_JSON)
                }
            }]
        })

    def log_message(self, format, *args):
        return

    def send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18999
    server = ThreadingHTTPServer(("0.0.0.0", port), FakeLlmHandler)
    print(f"fake llm listening on {port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
