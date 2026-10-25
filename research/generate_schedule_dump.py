#!/usr/bin/env python3
"""Генератор дампа планового расписания для зашивки в APK (assets/schedule.json).

Источник: страницы «маршрут + остановка» сайта btrans.by, например
https://minsk.btrans.by/avtobus/103/malinovka-7-128962
На каждой такой странице — полное табло: час -> «Буд.»/«Вых.» минуты,
а направление рейса — в заголовке h1 («остановка X → DEST»).

Выход: JSON
{
  "generated": "2026-10-21",
  "stops": [
    {"id": "malinovka-7", "label": "Малиновка 7",
     "routes": [
        {"route": "103", "board": "128962", "destination": "ДС Юго-Запад",
         "weekday": [3331, 3346, ...],   // минуты от полуночи
         "weekend": [...]}
     ]}
  ]
}
"""

import json
import re
import sys
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

BASE = "https://minsk.btrans.by"
UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"}

STOPS = [
    {"id": "malinovka-7", "label": "Малиновка 7"},
    {"id": "stancija-metro-malinovka", "label": "Ст. метро Малиновка"},
]
ROUTES = ["103", "114с", "150"]
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/schedule.json"

H1_RE = re.compile(r"<h1[^>]*>(.*?)</h1>", re.S)
ROW_RE = re.compile(r"<tr[^>]*>(.*?)</tr>", re.S)
CELL_RE = re.compile(r"<t[dh][^>]*>(.*?)</t[dh]>", re.S)
STOP_LINK_RE = re.compile(
    r'href="(/avtobus/%s/%s-[0-9]+)"' , re.I)


def fetch(url: str) -> str:
    # href может содержать сырую кириллицу — кодируем её, не трогая % и разделители
    url = urllib.parse.quote(url, safe="%/:=&?~-._")
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=25) as r:
        return r.read().decode("utf-8", "ignore")


def parse_timetable(html: str):
    """[(day_type, minutes_from_midnight)] из таблицы страницы."""
    table = re.search(r"<table.*?</table>", html, re.S)
    if not table:
        return []
    out = []
    hour = None
    for row in ROW_RE.findall(table.group(0)):
        cells = [re.sub(r"<[^>]+>", "", c).strip()
                 for c in CELL_RE.findall(row)]
        if not cells:
            continue
        if cells[0].isdigit():
            hour = int(cells[0])
            cells = cells[1:]
        day = None
        for c in cells:
            if c.startswith("Буд"):
                day = "weekday"
            elif c.startswith("Вых"):
                day = "weekend"
            elif day and hour is not None:
                for part in c.split():
                    if re.fullmatch(r"\d{1,2}", part):
                        out.append((day, hour * 60 + int(part)))
    return out


def parse_destination(html: str, stop_label: str):
    h1 = H1_RE.search(html)
    if not h1:
        return None
    text = re.sub(r"<[^>]+>", "", h1.group(1))
    m = re.search(r"остановк[аи]\s+(.+?)\s*→\s*(.+)$", text)
    return m.group(2).strip() if m else None


def main():
    result = {"generated": date.today().isoformat(), "stops": []}
    for stop in STOPS:
        entry = {"id": stop["id"], "label": stop["label"], "routes": []}
        for route in ROUTES:
            route_path = "/avtobus/" + urllib.parse.quote(route)
            route_html = fetch(BASE + route_path)
            # href содержит маршрут либо сырой кириллицей, либо %-encoding
            route_alts = {route, urllib.parse.quote(route),
                          urllib.parse.quote(route).lower()}
            route_pat = "(?:%s)" % "|".join(re.escape(a) for a in route_alts)
            links = re.findall(
                r'href="(?:https?://minsk\.btrans\.by)?'
                r'(/avtobus/%s/%s-(\d+))"' % (route_pat, stop["id"]),
                route_html, re.I)
            seen = set()
            for path, board in links:
                if (route, board) in seen:
                    continue
                seen.add((route, board))
                page_html = fetch(BASE + path)
                dest = parse_destination(page_html, stop["label"])
                times = parse_timetable(page_html)
                if not dest or not times:
                    print(f"WARN: {path}: dest={dest} times={len(times)}",
                          file=sys.stderr)
                    continue
                weekday = sorted(m for d, m in times if d == "weekday")
                weekend = sorted(m for d, m in times if d == "weekend")
                entry["routes"].append({
                    "route": route, "board": board, "destination": dest,
                    "weekday": weekday, "weekend": weekend,
                })
                print(f"{stop['id']} {route} board={board} -> {dest}: "
                      f"буд. {len(weekday)} рейсов, вых. {len(weekend)}")
        result["stops"].append(entry)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=1),
                   encoding="utf-8")
    print(f"OK -> {OUT}")


if __name__ == "__main__":
    main()
