#!/usr/bin/env python3
"""Прототип парсера прогноза со страницы остановки Яндекс Карт.

Использование: python3 parse_yandex_stop.py <файл.html | URL>
Вывод: маршрут | live-флаг | первая подача | следующие | назначание.

Найдено в ходе исследования (см. docs/data-sources.md): прогноз сервер
вшивает прямо в HTML страницы остановки вида
https://yandex.by/maps/157/minsk/stops/stop__<ID>/
Блок: <li class="masstransit-vehicle-snippet-view ...">, live-подача
помечена классом _live у masstransit-prognoses-view__title; если live-данных
нет, вместо минут показывается время по расписанию («19:31»).
"""

import re
import sys
import datetime

MSK = datetime.timezone(datetime.timedelta(hours=3))

ITEM_RE = re.compile(
    r'<li[^>]*masstransit-vehicle-snippet-view[^>]*>(.*?)</li>', re.S)
ROUTE_RE = re.compile(r'main-text">([^<]+)</span>')
DEST_RE = re.compile(r'aria-label="до [^"]*"><span>([^<]+)</span>')
FIRST_RE = re.compile(r'prognoses-view__title-text">([^<]+)</span>')
NEXT_RE = re.compile(r'prognoses-view__time">([^<]+)</div>')


def parse(html: str):
    for item in ITEM_RE.findall(html):
        route = ROUTE_RE.search(item)
        if not route:
            continue
        dest = DEST_RE.search(item)
        first = FIRST_RE.search(item)
        nxt = NEXT_RE.search(item)
        yield {
            'route': route.group(1),
            'to': dest.group(1) if dest else '',
            'live': '_live' in item,
            'first': first.group(1) if first else '',
            'next': nxt.group(1) if nxt else '',
        }


def load(source: str) -> str:
    if source.startswith('http'):
        import urllib.request
        req = urllib.request.Request(source, headers={
            'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36',
            'Accept-Language': 'ru',
        })
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.read().decode('utf-8', 'ignore')
    return open(source, encoding='utf-8', errors='ignore').read()


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    now = datetime.datetime.now(MSK)
    print(f"parse time: {now:%H:%M:%S}")
    for r in parse(load(sys.argv[1])):
        print(f"{r['route']:>6} | live={'Y' if r['live'] else 'N'} | "
              f"{r['first']:>8} | next: {r['next']:<20} | до {r['to']}")
