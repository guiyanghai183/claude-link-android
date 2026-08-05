#!/usr/bin/env bash
set -euo pipefail

SOURCE="${1:-mobile_claude_server.py}"
INSTALL_DIR="$HOME/.local/share/mobile-claude"
SERVICE_DIR="$HOME/.config/systemd/user"

install -d -m 700 "$INSTALL_DIR" "$SERVICE_DIR"
install -m 700 "$SOURCE" "$INSTALL_DIR/server.py"

cat >"$SERVICE_DIR/mobile-claude.service" <<EOF
[Unit]
Description=Mobile Claude loopback bridge
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/python3 $INSTALL_DIR/server.py --host 127.0.0.1 --port 18765
Restart=on-failure
RestartSec=2

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now mobile-claude.service
echo "Mobile Claude is running on 127.0.0.1:18765"
