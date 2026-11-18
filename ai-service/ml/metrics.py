"""Classification metrics implemented in numpy (no sklearn dependency)."""

from itertools import pairwise

import numpy as np


def roc_auc(y: np.ndarray, p: np.ndarray) -> float:
    """Mann-Whitney U formulation with average ranks for ties."""
    order = np.argsort(p, kind="mergesort")
    ranks = np.empty(len(p), dtype=float)
    sorted_p = p[order]
    i = 0
    while i < len(p):
        j = i
        while j + 1 < len(p) and sorted_p[j + 1] == sorted_p[i]:
            j += 1
        ranks[order[i : j + 1]] = (i + j) / 2 + 1
        i = j + 1
    n_pos = y.sum()
    n_neg = len(y) - n_pos
    if n_pos == 0 or n_neg == 0:
        return float("nan")
    return float((ranks[y == 1].sum() - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg))


def average_precision(y: np.ndarray, p: np.ndarray) -> float:
    """Area under the precision-recall curve (step-wise, ties grouped)."""
    order = np.argsort(-p, kind="mergesort")
    y_sorted, p_sorted = y[order], p[order]
    distinct = np.where(np.diff(p_sorted))[0]
    thresholds = np.r_[distinct, len(y) - 1]
    tp = np.cumsum(y_sorted)[thresholds]
    fp = (thresholds + 1) - tp
    precision = tp / (tp + fp)
    recall = tp / max(1, y.sum())
    recall_prev = np.r_[0, recall[:-1]]
    return float(np.sum((recall - recall_prev) * precision))


def brier(y: np.ndarray, p: np.ndarray) -> float:
    return float(np.mean((p - y) ** 2))


def log_loss(y: np.ndarray, p: np.ndarray) -> float:
    p = np.clip(p, 1e-7, 1 - 1e-7)
    return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))


def expected_calibration_error(y: np.ndarray, p: np.ndarray, bins: int = 10) -> float:
    edges = np.linspace(0, 1, bins + 1)
    ece = 0.0
    for lo, hi in pairwise(edges):
        mask = (p >= lo) & (p < hi) if hi < 1 else (p >= lo) & (p <= hi)
        if mask.any():
            ece += mask.mean() * abs(p[mask].mean() - y[mask].mean())
    return float(ece)


def recall_at_precision(y: np.ndarray, p: np.ndarray, min_precision: float) -> float:
    order = np.argsort(-p, kind="mergesort")
    tp = np.cumsum(y[order])
    precision = tp / np.arange(1, len(y) + 1)
    recall = tp / max(1, y.sum())
    ok = precision >= min_precision
    return float(recall[ok].max()) if ok.any() else 0.0


def summarize(y: np.ndarray, p: np.ndarray) -> dict:
    return {
        "rocAuc": round(roc_auc(y, p), 4),
        "prAuc": round(average_precision(y, p), 4),
        "brier": round(brier(y, p), 4),
        "logLoss": round(log_loss(y, p), 4),
        "ece": round(expected_calibration_error(y, p), 4),
        "recallAtPrecision50": round(recall_at_precision(y, p, 0.5), 4),
    }
