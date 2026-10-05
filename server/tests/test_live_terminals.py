import subprocess
import sys
import unittest
import uuid
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import mobile_claude_server as bridge


class LiveTerminalTests(unittest.TestCase):
    def setUp(self):
        self.session = str(uuid.uuid4())
        self.window = str(uuid.uuid4())
        self.target = 'claude-link-codex-' + uuid.UUID(self.window).hex[:24]
        self.source = {'pid': 123, 'start': '555', 'terminal': '/dev/pts/9', 'projectPath': '/home/test', 'updated': 1.0}

    def test_reused_pid_or_changed_terminal_cannot_be_transferred(self):
        with patch.object(bridge, 'live_cli_terminals', return_value={self.session: self.source}):
            for pid, start, tty in [(124, '555', '/dev/pts/9'), (123, '556', '/dev/pts/9'), (123, '555', '/dev/pts/10')]:
                with self.assertRaises(bridge.ChatBusyError):
                    bridge.validate_live_terminal('codex', pid, start, tty)
            self.assertEqual(bridge.validate_live_terminal('codex', 123, '555', '/dev/pts/9'), self.source)

    def test_empty_identity_is_rejected_before_execution(self):
        with self.assertRaises(bridge.ChatBusyError):
            bridge.validate_live_terminal('codex', 123, '', '/dev/pts/9')

    def test_existing_tmux_pane_is_shared_without_launch_or_transfer(self):
        calls = []
        def tmux(*args):
            calls.append(args)
            stdout = 'desktop-work\t/dev/pts/9\t@4\t%6\t\n' if args[0] == 'list-panes' else ''
            return subprocess.CompletedProcess([], 0, stdout, '')
        with patch.object(bridge, 'live_cli_terminals', return_value={self.session: self.source}), patch.object(bridge, 'tmux_command', side_effect=tmux), patch.object(bridge.subprocess, 'run') as run:
            self.assertEqual(bridge.attach_live_cli_terminal('codex', self.session, self.window), self.target)
        self.assertIn(('new-session', '-d', '-s', self.target, '-t', 'desktop-work'), calls)
        self.assertIn(('select-pane', '-t', '%6'), calls)
        self.assertIn(('set-option', '-t', self.target, '@claude_link_source_start', '555'), calls)
        run.assert_not_called()

    def test_already_transferred_terminal_reuses_proxy_without_second_takeover(self):
        result = subprocess.CompletedProcess([], 0, f'{self.target}\t/dev/pts/20\t@4\t%6\t/dev/pts/9\t123\t555\t0\n', '')
        with patch.object(bridge, 'live_cli_terminals', return_value={self.session: self.source}), patch.object(bridge, 'tmux_command', return_value=result) as tmux:
            self.assertEqual(bridge.attach_live_cli_terminal('codex', self.session, self.window), self.target)
        self.assertEqual(tmux.call_args_list[0].args[:2], ('list-panes', '-a'))
        self.assertTrue(all(call.args[0] != 'new-session' for call in tmux.call_args_list))

    def test_grouped_phone_alias_is_reused_even_if_desktop_pane_is_listed_first(self):
        panes = (f'desktop-work\t/dev/pts/9\t@4\t%6\t\t\t\t0\n'
                 f'{self.target}\t/dev/pts/9\t@4\t%6\t/dev/pts/9\t123\t555\t0\n')
        result = subprocess.CompletedProcess([], 0, panes, '')
        with patch.object(bridge, 'live_cli_terminals', return_value={self.session: self.source}), patch.object(bridge, 'tmux_command', return_value=result) as tmux:
            self.assertEqual(bridge.attach_live_cli_terminal('codex', self.session, self.window), self.target)
        self.assertTrue(all(call.args[0] != 'new-session' for call in tmux.call_args_list))

    def test_failed_transfer_preserves_original_error_and_only_removes_new_proxy(self):
        calls = []
        def tmux(*args):
            calls.append(args)
            text = ''
            if args[:2] == ('list-panes', '-t'): text = '1\n'
            if args[0] == 'capture-pane': text = 'Unable to attach: Permission denied'
            return subprocess.CompletedProcess([], 0, text, '')
        with patch.object(bridge, 'live_cli_terminals', return_value={self.session: self.source}), patch.object(bridge, 'tmux_command', side_effect=tmux), patch.object(bridge.shutil, 'which', side_effect=lambda name: '/usr/bin/'+name), patch.object(bridge.os, 'getuid', return_value=0, create=True), patch.object(bridge.time, 'sleep'):
            with self.assertRaisesRegex(bridge.ChatBusyError, 'Permission denied'):
                bridge.attach_live_cli_terminal('codex', self.session, self.window)
        self.assertEqual([c for c in calls if c[0] == 'kill-session'], [('kill-session', '-t', '='+self.target)])

    def test_child_revalidates_identity_before_privileged_execution(self):
        with patch.object(bridge, 'live_cli_terminals', return_value={}), patch.object(bridge.subprocess, 'Popen') as execute:
            with self.assertRaises(bridge.ChatBusyError):
                bridge.run_terminal_attachment('codex', 123, '555', '/dev/pts/9', self.target)
        execute.assert_not_called()

    def test_native_cli_transfers_through_its_same_user_terminal_leader(self):
        source = {**self.source, 'sid': '88'}
        with patch.object(bridge.Path, 'stat', return_value=SimpleNamespace(st_uid=1000)), patch.object(bridge.os, 'getuid', return_value=1000, create=True), patch.object(bridge.os, 'readlink', return_value=source['terminal']):
            self.assertEqual(bridge.reptyr_target(source), 88)

    def test_terminal_leader_with_other_owner_or_other_tty_is_rejected(self):
        source = {**self.source, 'sid': '88'}
        for owner, tty in [(1001, source['terminal']), (1000, '/dev/pts/10')]:
            with self.subTest(owner=owner, tty=tty), patch.object(bridge.Path, 'stat', return_value=SimpleNamespace(st_uid=owner)), patch.object(bridge.os, 'getuid', return_value=1000, create=True), patch.object(bridge.os, 'readlink', return_value=tty):
                with self.assertRaises(bridge.ChatBusyError):
                    bridge.reptyr_target(source)

    def test_exited_terminal_leader_is_rejected_before_transfer(self):
        with patch.object(bridge.Path, 'stat', side_effect=FileNotFoundError):
            with self.assertRaisesRegex(bridge.ChatBusyError, '启动器已退出'):
                bridge.reptyr_target({**self.source, 'sid': '88'})

    def test_alive_proxy_does_not_mean_the_original_terminal_is_ready(self):
        results = [subprocess.CompletedProcess([], 0, text, '') for text in ['0\t\n', '0\t1\n', '']]
        with patch.object(bridge, 'tmux_command', side_effect=results) as tmux, patch.object(bridge.time, 'sleep') as pause:
            bridge.wait_for_terminal_attachment(self.target)
        pause.assert_called_once()
        self.assertEqual(tmux.call_args_list[-1].args, ('set-option', '-t', self.target, '@claude_link_attach_pending', '0'))

    def test_a_slow_pending_transfer_is_not_killed_on_timeout(self):
        with patch.object(bridge.time, 'monotonic', side_effect=[0, 21]), patch.object(bridge, 'tmux_command') as tmux:
            with self.assertRaisesRegex(bridge.ChatBusyError, '保持原 SSH'):
                bridge.wait_for_terminal_attachment(self.target)
        tmux.assert_not_called()

    def test_migrated_tty_maps_to_phone_window_after_database_entry_was_removed(self):
        result = subprocess.CompletedProcess([], 0, f'{self.target}\t/dev/pts/20\t/dev/pts/9\t123\t555\t0\n', '')
        with patch.object(bridge, 'active_cli_sessions', return_value={self.session: '/dev/pts/9'}), patch.object(bridge, 'live_cli_terminals', return_value={self.session: self.source}), patch.object(bridge.shutil, 'which', return_value='/usr/bin/tmux'), patch.object(bridge.subprocess, 'run', return_value=result):
            mapping = bridge.cli_tmux_window_ids([], 'codex')
        self.assertEqual(uuid.UUID(mapping[self.session]).hex[:24], uuid.UUID(self.window).hex[:24])


if __name__ == '__main__':
    unittest.main()
