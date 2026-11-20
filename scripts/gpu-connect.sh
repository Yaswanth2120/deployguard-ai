#!/usr/bin/env bash
# Configure a vast.ai vLLM-template instance for DeployGuard evals and tunnel it to this machine.
#
# Usage (run on your laptop, not on the GPU):
#   scripts/gpu-connect.sh -p <ssh-port> root@<ip> [model]
# Example:
#   scripts/gpu-connect.sh -p 21329 root@213.199.247.188 openai/gpt-oss-120b
#
# What it does:
#   1. On the GPU: sets vLLM args the project needs (tool calling, 16k context, 8 concurrent
#      sequences) via /etc/vllm-args.conf, which the template appends last so it overrides
#      the template defaults; optionally switches the model; restarts the supervisor service
#      and kills any orphaned vLLM server still holding the port and GPU memory.
#   2. Waits until the model reports the new context length.
#   3. Opens an SSH tunnel: localhost:18000 -> GPU 127.0.0.1:18000 (never exposed publicly).
#
# Then run evals with:
#   OPENROUTER_API_KEY=local OPENROUTER_BASE_URL=http://localhost:18000/v1 OPENROUTER_MODEL=<model> \
#     PYTHONPATH=. python -m evals.run --mode rag --mode agent
set -euo pipefail

if [[ $# -lt 3 || $1 != "-p" ]]; then
  sed -n '2,12p' "$0"
  exit 1
fi
PORT=$2
HOST=$3
MODEL=${4:-openai/gpt-oss-120b}
LOCAL_PORT=${LOCAL_PORT:-18000}
SSH=(ssh -q -p "$PORT" -o BatchMode=yes -o ConnectTimeout=15 -o StrictHostKeyChecking=accept-new "$HOST")

case "$MODEL" in
  openai/gpt-oss*) PARSER=openai ;;
  *) PARSER=hermes ;;  # Qwen and most other open models
esac

echo "Configuring $MODEL on $HOST (tool parser: $PARSER)..."
"${SSH[@]}" bash -s -- "$MODEL" "$PARSER" <<'REMOTE' 2>/dev/null | grep -v "^Welcome\|^Have fun\|^AI agents"
set -euo pipefail
MODEL=$1
PARSER=$2
BASE='--host 127.0.0.1 --port 18000'
[[ -f /etc/vllm-args.conf.orig ]] || cp /etc/vllm-args.conf /etc/vllm-args.conf.orig 2>/dev/null || echo "$BASE" > /etc/vllm-args.conf.orig
echo "$(cat /etc/vllm-args.conf.orig) --max-model-len 16384 --max-num-seqs 8 --gpu-memory-utilization 0.92 --enable-auto-tool-choice --tool-call-parser $PARSER" > /etc/vllm-args.conf
if [[ "${VLLM_MODEL:-}" != "$MODEL" ]]; then
  sed -i '/^VLLM_MODEL=/d;/^MODEL_NAME=/d' /etc/environment
  printf 'VLLM_MODEL="%s"\nMODEL_NAME="%s"\n' "$MODEL" "$MODEL" >> /etc/environment
fi
supervisorctl stop vllm >/dev/null || true
# The template's restart can leave the old server orphaned, holding the port and GPU memory.
pkill -f "vllm serve" || true
pkill -f "VLLM::EngineCore" || true
for _ in $(seq 1 30); do pgrep -f "vllm serve|VLLM::EngineCore" >/dev/null || break; sleep 2; done
pkill -9 -f "vllm serve|VLLM::EngineCore" || true
supervisorctl start vllm
REMOTE

echo "Waiting for the model to load (first download of a large model can take 10+ minutes)..."
for _ in $(seq 1 120); do
  if "${SSH[@]}" 'curl -s -m 3 localhost:18000/v1/models' 2>/dev/null | grep -q '"max_model_len":16384'; then
    READY=1
    break
  fi
  if "${SSH[@]}" 'supervisorctl status vllm' 2>/dev/null | grep -qE "FATAL|EXITED"; then
    echo "vLLM failed to start. Last log lines:"
    "${SSH[@]}" 'tail -20 /var/log/portal/vllm.log' 2>/dev/null
    exit 1
  fi
  sleep 15
done
[[ ${READY:-0} == 1 ]] || { echo "Timed out waiting for vLLM."; exit 1; }

if ! curl -s -m 3 "localhost:$LOCAL_PORT/v1/models" >/dev/null 2>&1; then
  ssh -q -p "$PORT" -o BatchMode=yes -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=8 \
    -fN -L "$LOCAL_PORT:127.0.0.1:18000" "$HOST"
fi
curl -s -m 5 "localhost:$LOCAL_PORT/v1/models" | grep -o '"id":"[^"]*"' | head -1
echo "Ready: http://localhost:$LOCAL_PORT/v1"
