import http.client
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_claude_server import (  # noqa: E402
    BridgeServer, ServiceState, Store, active_cli_session_ids,
    cli_tmux_window_ids, discover_cli_sessions,
)


def write_session(home, mode, session_id, project, *, source="cli", title="已有对话"):
    if mode == "qodercn":
        path = home / ".qoder-cn/projects/project" / f"{session_id}.jsonl"
        rows = [
            {"type": "user", "sessionId": session_id, "cwd": str(project), "message": {"content": "继续已有任务"}},
            {"type": "ai-title", "sessionId": session_id, "aiTitle": title},
        ]
    else:
        path = home / ".codex/sessions/2026/10/05" / f"rollout-test-{session_id}.jsonl"
        rows = [
            {"type": "session_meta", "payload": {"id": session_id, "cwd": str(project), "source": source}},
            {"type": "response_item", "payload": {"role": "user", "content": [{"type": "input_text", "text": "# AGENTS.md instructions"}]}},
            {"type": "event_msg", "payload": {"type": "user_message", "message": title}},
        ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n", encoding="utf-8")
    return path


class CliSessionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.home = Path(self.temp.name)
        self.project = self.home / "project"
        self.project.mkdir()
        self.store = Store(self.home / "history.sqlite3")

    def tearDown(self):
        self.store.connection().close()
        self.temp.cleanup()

    def test_mixed_windows_share_limit_and_keep_independent_qoder_session_ids(self):
        windows = [self.store.create_chat(str(self.project), mode=mode)
                   for mode in ["codex", "qodercn"] * 3]
        qoder = [window for window in windows if window["mode"] == "qodercn"]
        self.assertEqual([window["title"] for window in qoder], ["Qoder CN 1", "Qoder CN 2", "Qoder CN 3"])
        self.assertEqual(len({window["cliSessionId"] for window in qoder}), 3)
        self.assertTrue(all(window["pinned"] for window in windows))
        self.assertEqual(self.store.connection().execute("SELECT COUNT(*) FROM claude_sessions").fetchone()[0], 0)
        with self.assertRaisesRegex(ValueError, "最多只能创建 6 个"):
            self.store.create_chat(str(self.project), mode="qodercn")
        duplicate = self.store.create_chat(str(self.project), mode="qodercn", cli_session_id=qoder[0]["cliSessionId"])
        self.assertEqual(duplicate["id"], qoder[0]["id"])

    def test_reopening_database_keeps_legacy_codex_and_new_provider_history(self):
        old = self.store.create_chat(str(self.project), mode="codex")
        session_id = str(uuid.uuid4())
        new = self.store.create_chat(str(self.project), mode="qodercn", cli_session_id=session_id)
        reopened = Store(self.home / "history.sqlite3")
        try:
            self.assertIsNone(reopened.get_chat(old["id"])["cliSessionId"])
            self.assertEqual(reopened.get_chat(new["id"])["cliSessionId"], session_id)
        finally:
            reopened.connection().close()

    def test_existing_schema_migrates_without_losing_codex_row(self):
        path = self.home / "old.sqlite3"
        with sqlite3.connect(path) as connection:
            connection.execute("CREATE TABLE chats(id TEXT PRIMARY KEY,title TEXT,project_path TEXT,mode TEXT,created_at REAL,updated_at REAL,pinned INTEGER,claude_started INTEGER,status TEXT,last_error TEXT)")
            connection.execute("INSERT INTO chats VALUES('old','Codex 1',?,'codex',1,1,1,0,'idle',NULL)", (str(self.project),))
        connection.close()
        migrated = Store(path)
        try:
            self.assertEqual(migrated.get_chat("old")["mode"], "codex")
            self.assertIsNone(migrated.get_chat("old")["cliSessionId"])
        finally:
            migrated.connection().close()

    def test_qoder_metadata_prefers_custom_title_and_does_not_scan_subagents(self):
        session_id = str(uuid.uuid4())
        path = write_session(self.home, "qodercn", session_id, self.project)
        with path.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps({"type": "custom-title", "sessionId": session_id, "customTitle": "用户指定标题"}, ensure_ascii=False) + "\n")
        child = path.parent / "subagents" / f"{uuid.uuid4()}.jsonl"
        child.parent.mkdir()
        child.write_bytes(path.read_bytes())
        sessions = discover_cli_sessions("qodercn", self.home)
        self.assertEqual(len(sessions), 1)
        self.assertEqual(sessions[0]["title"], "用户指定标题")
        self.assertEqual(sessions[0]["projectPath"], str(self.project))

    def test_codex_metadata_skips_automated_jobs_and_reads_saved_title(self):
        session_id = str(uuid.uuid4())
        write_session(self.home, "codex", session_id, self.project, title="真正的问题")
        write_session(self.home, "codex", str(uuid.uuid4()), self.project, source="exec")
        write_session(self.home, "codex", str(uuid.uuid4()), self.project, source={"subagent": "task"})
        index = self.home / ".codex/session_index.jsonl"
        index.write_text(json.dumps({"id": session_id, "thread_name": "保存的 Codex 标题"}, ensure_ascii=False) + "\n", encoding="utf-8")
        with index.open("a", encoding="utf-8") as stream:
            stream.write('{"thread_name":"missing ID"}\n')
        sessions = discover_cli_sessions("codex", self.home)
        self.assertEqual(len(sessions), 1)
        self.assertEqual(sessions[0]["title"], "保存的 Codex 标题")
        self.assertEqual(sessions[0]["preview"], "真正的问题")

    def test_large_qoder_history_reads_final_title_and_ignores_partial_records(self):
        session_id = str(uuid.uuid4())
        path = write_session(self.home, "qodercn", session_id, self.project)
        with path.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps({"type": "assistant", "sessionId": session_id, "message": "x" * 300_000}) + "\n")
            stream.write(json.dumps({"type": "custom-title", "sessionId": session_id, "customTitle": "最后的标题"}, ensure_ascii=False) + "\n")
            stream.write('{"type":')
        self.assertEqual(discover_cli_sessions("qodercn", self.home)[0]["title"], "最后的标题")

    def test_invalid_mode_and_invalid_qoder_file_names_are_rejected(self):
        path = self.home / ".qoder-cn/projects/project/not-a-session.jsonl"
        path.parent.mkdir(parents=True)
        path.write_text('{}\n')
        self.assertEqual(discover_cli_sessions("qodercn", self.home), [])
        with self.assertRaises(ValueError):
            discover_cli_sessions("../auth", self.home)

    def test_active_process_identity_is_read_without_leaking_arguments(self):
        session_id = str(uuid.uuid4())
        proc = self.home / "proc"
        process = proc / "123"
        (process / "fd").mkdir(parents=True)
        (process / "comm").write_text("qoderclicn\n")
        (process / "cmdline").write_bytes(f"qoderclicn\0--resume\0{session_id}\0private prompt\0".encode())
        (process / "fd/0").touch()
        with patch("mobile_claude_server.os.getuid", return_value=process.stat().st_uid, create=True), patch("mobile_claude_server.os.readlink", return_value="/dev/pts/1"):
            self.assertEqual(active_cli_session_ids("qodercn", proc), {session_id})

    def test_legacy_codex_window_is_recognized_from_live_tmux_terminal(self):
        chat = self.store.create_chat(str(self.project), mode="codex")
        session_id = str(uuid.uuid4())
        name = "claude-link-codex-" + chat["id"].replace("-", "")[:24]
        result = subprocess.CompletedProcess([], 0, stdout=f"{name}\t/dev/pts/9\nother-window\t/dev/pts/8\n", stderr="")
        with patch("mobile_claude_server.active_cli_sessions", return_value={session_id: "/dev/pts/9"}), patch("mobile_claude_server.shutil.which", return_value="/usr/bin/tmux"), patch("mobile_claude_server.subprocess.run", return_value=result) as run:
            self.assertEqual(cli_tmux_window_ids([chat], "codex"), {session_id: chat["id"]})
        self.assertNotIn("LD_LIBRARY_PATH", run.call_args.kwargs["env"])
        self.assertEqual(run.call_args.args[0][1:4], ["list-panes", "-a", "-F"])

    def test_node_codex_launcher_is_interactive_even_when_using_a_profile(self):
        from mobile_claude_server import live_cli_terminals
        proc = self.home / "node-proc"
        process = proc / "123"
        (process / "fd").mkdir(parents=True)
        (process / "fd/0").touch()
        (process / "comm").write_text("node\n")
        (process / "cmdline").write_bytes(f"node\0/usr/local/bin/codex\0-p\0work\0-C\0{self.project}\0".encode())
        with patch("mobile_claude_server.os.getuid", return_value=process.stat().st_uid, create=True), patch("mobile_claude_server.os.readlink", return_value="/dev/pts/1"):
            listed = live_cli_terminals("codex", proc)
        self.assertEqual(len(listed), 1)
        self.assertEqual(next(iter(listed.values()))["projectPath"], str(self.project))

    def test_other_node_apps_and_noninteractive_codex_are_not_attachable(self):
        from mobile_claude_server import live_cli_terminals
        proc = self.home / "noninteractive-proc"
        process = proc / "123"
        (process / "fd").mkdir(parents=True)
        (process / "fd/0").touch()
        (process / "comm").write_text("node\n")
        for args in ["node\0/other/server.js\0codex\0", "node\0/usr/local/bin/codex\0exec\0hello\0"]:
            (process / "cmdline").write_bytes(args.encode())
            with patch("mobile_claude_server.os.getuid", return_value=process.stat().st_uid, create=True), patch("mobile_claude_server.os.readlink", return_value="/dev/pts/1"):
                self.assertEqual(live_cli_terminals("codex", proc), {})

    def test_npm_launcher_and_native_child_on_same_tty_are_listed_once(self):
        from mobile_claude_server import live_cli_terminals
        session_id = str(uuid.uuid4())
        proc = self.home / "launcher-proc"
        for pid, comm, command in [
            ("123", "node", "node\0/usr/local/bin/codex\0"),
            ("124", "codex", "codex\0"),
        ]:
            p = proc / pid
            (p / "fd").mkdir(parents=True)
            (p / "fd/0").touch()
            (p / "fd/1").touch()
            (p / "comm").write_text(comm)
            (p / "cmdline").write_bytes(command.encode())
        def link(path):
            if path.name == "cwd":
                return str(self.project)
            if path.parent.parent.name == "124" and path.name == "1":
                return f"/home/test/.codex/sessions/2026/10/05/rollout-test-{session_id}.jsonl"
            return "/dev/pts/7"
        with patch("mobile_claude_server.os.getuid", return_value=proc.stat().st_uid, create=True), patch("mobile_claude_server.os.readlink", side_effect=link):
            sessions = live_cli_terminals("codex", proc)
        self.assertEqual(list(sessions), [session_id])
        self.assertEqual(sessions[session_id]["pid"], 124)


class CliSessionHttpTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.home = Path(self.temp.name)
        self.project = self.home / "project"
        self.project.mkdir()
        self.home_patch = patch("mobile_claude_server.Path.home", return_value=self.home)
        self.home_patch.start()
        self.state = ServiceState(self.home / "data", claude_command="false")
        self.server = BridgeServer(("127.0.0.1", 0), self.state)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.thread.join(timeout=3)
        self.server.server_close()
        self.state.close()
        self.home_patch.stop()
        self.temp.cleanup()

    def request(self, method, endpoint, body=None):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=5)
        try:
            payload = json.dumps(body).encode() if body is not None else None
            connection.request(method, endpoint, body=payload, headers={"Content-Type": "application/json"})
            response = connection.getresponse()
            return response.status, json.loads(response.read())
        finally:
            connection.close()

    def test_both_providers_restore_context_and_repeated_restore_reuses_window(self):
        for mode in ["codex", "qodercn"]:
            session_id = str(uuid.uuid4())
            write_session(self.home, mode, session_id, self.project)
            previous = self.state.store.list_chats()
            status, listed = self.request("GET", f"/v1/{mode}/sessions")
            self.assertEqual(status, 200)
            self.assertEqual(listed["sessions"][0]["id"], session_id)
            self.assertEqual(self.state.store.list_chats(), previous)
            body = {"mode": mode, "resumeSessionId": session_id, "projectPath": str(self.home), "clientChatId": str(uuid.uuid4())}
            status, window = self.request("POST", "/v1/chats", body)
            self.assertEqual(status, 201)
            self.assertEqual(window["projectPath"], str(self.project))
            self.assertEqual(window["cliSessionId"], session_id)
            body["clientChatId"] = str(uuid.uuid4())
            duplicate_status, duplicate = self.request("POST", "/v1/chats", body)
            self.assertEqual(duplicate_status, 201)
            self.assertEqual(duplicate["id"], window["id"])
        self.assertEqual(len(self.state.store.list_chats()), 2)

    def test_running_external_session_is_attached_once_and_repeated_restore_reuses_it(self):
        session_id = str(uuid.uuid4())
        write_session(self.home, "qodercn", session_id, self.project)
        target = "claude-link-qodercn-" + "a" * 24
        with patch("mobile_claude_server.active_cli_session_ids", return_value={session_id}), patch("mobile_claude_server.attach_live_cli_terminal", return_value=target) as attach:
            status, listed = self.request("GET", "/v1/qodercn/sessions")
            self.assertEqual(status, 200)
            self.assertTrue(listed["sessions"][0]["running"])
            status, window = self.request("POST", "/v1/chats", {"mode": "qodercn", "resumeSessionId": session_id})
            again, reopened = self.request("POST", "/v1/chats", {"mode": "qodercn", "resumeSessionId": session_id})
        self.assertEqual(status, 201)
        self.assertEqual(again, 201)
        self.assertEqual(reopened["id"], window["id"])
        self.assertEqual(window["sharedTerminalTarget"], target)
        attach.assert_called_once_with("qodercn", session_id, window["id"])
        self.assertEqual(len(self.state.store.list_chats()), 1)

    def test_failed_attachment_removes_only_its_new_phone_entry(self):
        session_id = str(uuid.uuid4())
        write_session(self.home, "codex", session_id, self.project)
        existing = self.state.store.create_chat(str(self.project), mode="codex")
        from mobile_claude_server import ChatBusyError
        with patch("mobile_claude_server.active_cli_session_ids", return_value={session_id}), patch("mobile_claude_server.attach_live_cli_terminal", side_effect=ChatBusyError("测试权限错误")):
            status, error = self.request("POST", "/v1/chats", {"mode": "codex", "resumeSessionId": session_id})
        self.assertEqual(status, 409)
        self.assertEqual(error["error"], "测试权限错误")
        self.assertEqual([chat["id"] for chat in self.state.store.list_chats()], [existing["id"]])

    def test_window_limit_is_checked_before_terminal_takeover(self):
        session_id = str(uuid.uuid4())
        write_session(self.home, "qodercn", session_id, self.project)
        for _ in range(6):
            self.state.store.create_chat(str(self.project), mode="codex")
        with patch("mobile_claude_server.active_cli_session_ids", return_value={session_id}), patch("mobile_claude_server.attach_live_cli_terminal") as attach:
            status, _ = self.request("POST", "/v1/chats", {"mode": "qodercn", "resumeSessionId": session_id})
        self.assertEqual(status, 400)
        attach.assert_not_called()

    def test_empty_live_terminal_can_be_listed_and_attached_without_history_file(self):
        session_id = str(uuid.uuid4())
        source = {"pid": 123, "start": "555", "terminal": "/dev/pts/9", "projectPath": str(self.project), "updated": 1.0}
        target = "claude-link-codex-" + "b" * 24
        with patch("mobile_claude_server.live_cli_terminals", return_value={session_id: source}), patch("mobile_claude_server.cli_tmux_window_ids", return_value={}), patch("mobile_claude_server.attach_live_cli_terminal", return_value=target):
            status, listed = self.request("GET", "/v1/codex/sessions")
            self.assertEqual(status, 200)
            self.assertTrue(listed["sessions"][0]["running"])
            self.assertEqual(listed["sessions"][0]["id"], session_id)
            status, window = self.request("POST", "/v1/chats", {"mode": "codex", "resumeSessionId": session_id})
        self.assertEqual(status, 201)
        self.assertEqual(window["sharedTerminalTarget"], target)

    def test_existing_phone_window_is_prepared_for_a_conversation_running_in_ssh(self):
        session_id = str(uuid.uuid4())
        window = self.state.store.create_chat(str(self.project), mode="codex", cli_session_id=session_id)
        source = {"pid": 123, "start": "555", "terminal": "/dev/pts/9", "projectPath": str(self.project), "updated": 1.0}
        target = "claude-link-codex-" + uuid.UUID(window["id"]).hex[:24]
        with patch("mobile_claude_server.live_cli_terminals", return_value={session_id: source}), patch("mobile_claude_server.cli_tmux_window_ids", return_value={}), patch("mobile_claude_server.attach_live_cli_terminal", return_value=target) as attach:
            status, prepared = self.request("POST", f"/v1/chats/{window['id']}/cli/open", {})
        self.assertEqual(status, 200)
        self.assertEqual(prepared["sharedTerminalTarget"], target)
        attach.assert_called_once_with("codex", session_id, window["id"])

    def test_preparing_active_phone_tmux_does_not_transfer_the_cli(self):
        session_id = str(uuid.uuid4())
        window = self.state.store.create_chat(str(self.project), mode="qodercn", cli_session_id=session_id)
        result = subprocess.CompletedProcess([], 0, "", "")
        with patch("mobile_claude_server.live_cli_terminals", return_value={session_id: {}}), patch("mobile_claude_server.cli_tmux_window_ids", return_value={session_id: window["id"]}), patch("mobile_claude_server.tmux_command", return_value=result), patch("mobile_claude_server.attach_live_cli_terminal") as attach:
            status, prepared = self.request("POST", f"/v1/chats/{window['id']}/cli/open", {})
        self.assertEqual(status, 200)
        self.assertIsNone(prepared["sharedTerminalTarget"])
        attach.assert_not_called()

    def test_preparing_an_ordinary_claude_chat_rejects_terminal_transfer(self):
        window = self.state.store.create_chat(str(self.project))
        status, _ = self.request("POST", f"/v1/chats/{window['id']}/cli/open", {})
        self.assertEqual(status, 400)

    def test_unidentified_active_thread_does_not_clone_a_recent_history(self):
        saved = str(uuid.uuid4())
        active = str(uuid.uuid4())
        write_session(self.home, "codex", saved, self.project)
        source = {"pid": 123, "start": "555", "terminal": "/dev/pts/9", "projectPath": str(self.project),
                  "updated": 1.0, "knownSession": False, "startedAt": 0.0}
        target = "claude-link-codex-" + "a" * 24
        with patch("mobile_claude_server.live_cli_terminals", return_value={active: source}), patch("mobile_claude_server.cli_tmux_window_ids", return_value={}), patch("mobile_claude_server.attach_live_cli_terminal", return_value=target) as attach:
            status, error = self.request("POST", "/v1/chats", {"mode": "codex", "resumeSessionId": saved})
            self.assertEqual(status, 409)
            self.assertIn("正在使用的终端", error["error"])
            attach.assert_not_called()
            self.assertEqual(self.state.store.list_chats(), [])
            status, window = self.request("POST", "/v1/chats", {"mode": "codex", "resumeSessionId": active})
        self.assertEqual(status, 201)
        self.assertEqual(window["sharedTerminalTarget"], target)

    def test_restore_missing_or_malformed_id_does_not_create_a_new_conversation(self):
        for session_id, expected in [(str(uuid.uuid4()), 404), ("not-a-uuid", 400)]:
            status, _ = self.request("POST", "/v1/chats", {"mode": "codex", "resumeSessionId": session_id})
            self.assertEqual(status, expected)
        self.assertEqual(self.state.store.list_chats(), [])

    def test_recognized_legacy_window_is_reused_even_at_window_limit(self):
        session_id = str(uuid.uuid4())
        write_session(self.home, "codex", session_id, self.project)
        windows = [self.state.store.create_chat(str(self.project), mode="codex") for _ in range(6)]
        with patch("mobile_claude_server.cli_tmux_window_ids", return_value={session_id: windows[0]["id"]}), patch("mobile_claude_server.active_cli_session_ids", return_value={session_id}):
            status, window = self.request("POST", "/v1/chats", {"mode": "codex", "resumeSessionId": session_id})
        self.assertEqual(status, 201)
        self.assertEqual(window["id"], windows[0]["id"])
        self.assertEqual(len(self.state.store.list_chats()), 6)

    def test_app_owned_live_window_can_be_reregistered_without_spawning_a_second_tmux(self):
        session_id = str(uuid.uuid4())
        write_session(self.home, "codex", session_id, self.project)
        suffix = uuid.uuid4().hex[:24]
        live_name = "claude-link-codex-" + suffix
        orphan_id = str(uuid.UUID(hex=suffix + "00000000"))
        result = subprocess.CompletedProcess([], 0, stdout=f"{live_name}\t/dev/pts/9\n", stderr="")
        with patch("mobile_claude_server.active_cli_sessions", return_value={session_id: "/dev/pts/9"}), patch("mobile_claude_server.active_cli_session_ids", return_value={session_id}), patch("mobile_claude_server.shutil.which", return_value="/usr/bin/tmux"), patch("mobile_claude_server.subprocess.run", return_value=result), patch("mobile_claude_server.attach_live_cli_terminal", return_value=live_name):
            status, window = self.request("POST", "/v1/chats", {"mode": "codex", "resumeSessionId": session_id})
        self.assertEqual(status, 201)
        self.assertEqual(window["id"], orphan_id)
        self.assertEqual("claude-link-codex-" + window["id"].replace("-", "")[:24], live_name)
        self.assertEqual(window["cliSessionId"], session_id)


if __name__ == "__main__":
    unittest.main()
