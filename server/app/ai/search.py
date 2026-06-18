"""联网搜索与网页抓取。"""

import html
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from html.parser import HTMLParser
from urllib.parse import parse_qs, unquote, urlparse

import requests
from flask import current_app

_USER_AGENT = (
    "Mozilla/5.0 (compatible; MindShelf/1.0; +https://github.com/mindshelf)"
)

_session: requests.Session | None = None


def _search_cfg() -> dict:
    return current_app.config.get("MINDSHELF_CONFIG", {}).get("search", {})


def is_enabled() -> bool:
    return bool(_search_cfg().get("enabled", True))


def _max_results() -> int:
    return int(_search_cfg().get("max_results", 5))


def _fetch_pages_enabled() -> bool:
    return bool(_search_cfg().get("fetch_pages", True))


def _max_fetch_pages() -> int:
    return int(_search_cfg().get("max_fetch_pages", 5))


def _min_content_chars() -> int:
    """已有正文字数达到该阈值则不再重复抓取。"""
    return int(_search_cfg().get("min_content_chars", 200))


def _page_max_chars() -> int:
    return int(_search_cfg().get("page_max_chars", 2500))


def _search_timeout() -> int:
    return int(_search_cfg().get("search_timeout", 8))


def _page_timeout() -> int:
    return int(_search_cfg().get("page_timeout", 8))


def _http_session() -> requests.Session:
    global _session
    if _session is None:
        _session = requests.Session()
        _session.headers.update({"User-Agent": _USER_AGENT})
    return _session


def _request(url: str, *, method: str = "GET", data: dict | None = None, timeout: int | None = None) -> str:
    limit = timeout if timeout is not None else _search_timeout()
    resp = _http_session().request(method, url, data=data, timeout=limit)
    resp.raise_for_status()
    resp.encoding = resp.apparent_encoding or "utf-8"
    return resp.text


def _resolve_ddg_url(href: str) -> str:
    if not href:
        return ""
    if href.startswith("//"):
        href = f"https:{href}"
    parsed = urlparse(href)
    if "duckduckgo.com" in parsed.netloc and parsed.path.startswith("/l/"):
        uddg = parse_qs(parsed.query).get("uddg", [""])[0]
        if uddg:
            return unquote(uddg)
    return href


def web_search(
    query: str,
    max_results: int | None = None,
    *,
    sort: str | None = None,
    time_range: str | None = None,
) -> list[dict]:
    """联网搜索，返回 title / url / snippet（可选 content）列表。"""
    query = query.strip()
    if not query:
        return []

    limit = max_results if max_results is not None else _max_results()
    provider = _search_cfg().get("provider", "duckduckgo")
    if provider == "uapi":
        return _search_uapi(query, limit, sort=sort, time_range=time_range)
    if provider == "duckduckgo":
        # Instant Answer API 更快，优先使用；结果不足时再走 HTML 搜索
        results = _search_ddg_api(query, limit)
        if len(results) < min(3, limit):
            html_results = _search_ddg_html(query, limit)
            seen = {item.get("url") for item in results}
            for item in html_results:
                url = item.get("url")
                if url and url not in seen:
                    results.append(item)
                    seen.add(url)
                if len(results) >= limit:
                    break
        return results[:limit]
    if provider == "ddg_api":
        return _search_ddg_api(query, limit)
    return []


def _search_uapi(
    query: str,
    limit: int,
    *,
    sort: str | None = None,
    time_range: str | None = None,
) -> list[dict]:
    """UAPI Pro 聚合搜索：https://uapis.cn/docs/api-reference/post-search-aggregate"""
    cfg = _search_cfg()
    api_key = (cfg.get("api_key") or "").strip()
    if not api_key:
        return []

    url = cfg.get("api_url", "https://uapis.cn/api/v1/search/aggregate")
    # UAPI 仅作聚合搜索；正文统一由本服务 fetch_page 抓取
    payload: dict = {
        "query": query,
        "fetch_full": False,
    }
    effective_sort = sort or cfg.get("sort")
    if effective_sort:
        payload["sort"] = effective_sort
    effective_range = time_range or cfg.get("time_range")
    if effective_range:
        payload["time_range"] = effective_range

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    try:
        resp = _http_session().post(
            url,
            json=payload,
            headers=headers,
            timeout=_search_timeout(),
        )
        resp.raise_for_status()
        data = resp.json()
    except requests.RequestException:
        return []

    results: list[dict] = []
    for item in (data.get("results") or [])[:limit]:
        title = (item.get("title") or "").strip()
        link = (item.get("url") or "").strip()
        if not title or not link:
            continue
        results.append(
            {
                "title": title,
                "url": link,
                "snippet": (item.get("snippet") or "").strip(),
            }
        )
    return results


def _search_ddg_html(query: str, limit: int) -> list[dict]:
    try:
        body = _request(
            "https://html.duckduckgo.com/html/",
            method="POST",
            data={"q": query},
        )
    except requests.RequestException:
        return []

    results: list[dict] = []
    for block in re.findall(r'<div class="result[^"]*"[^>]*>.*?</div>\s*</div>', body, re.S):
        title_match = re.search(
            r'class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>',
            block,
            re.S,
        )
        if not title_match:
            continue
        url = _resolve_ddg_url(html.unescape(title_match.group(1)))
        title = re.sub(r"<[^>]+>", "", title_match.group(2))
        title = html.unescape(title).strip()
        snippet_match = re.search(
            r'class="result__snippet"[^>]*>(.*?)</(?:a|td|span|div)>',
            block,
            re.S,
        )
        snippet = ""
        if snippet_match:
            snippet = re.sub(r"<[^>]+>", "", snippet_match.group(1))
            snippet = html.unescape(snippet).strip()
        if url and title:
            results.append({"title": title, "url": url, "snippet": snippet})
        if len(results) >= limit:
            break
    return results


def _search_ddg_api(query: str, limit: int) -> list[dict]:
    """Instant Answer API 兜底，结果较少但无需 HTML 解析。"""
    try:
        resp = _http_session().get(
            "https://api.duckduckgo.com/",
            params={"q": query, "format": "json", "no_redirect": 1, "no_html": 1},
            timeout=_search_timeout(),
        )
        resp.raise_for_status()
        data = resp.json()
    except requests.RequestException:
        return []

    results: list[dict] = []
    abstract_url = data.get("AbstractURL") or ""
    if abstract_url:
        results.append(
            {
                "title": data.get("Heading") or query,
                "url": abstract_url,
                "snippet": data.get("Abstract") or "",
            }
        )

    def _append_topic(item: dict) -> None:
        if len(results) >= limit:
            return
        url = item.get("FirstURL") or ""
        text = item.get("Text") or ""
        if url and text:
            title = text.split(" - ", 1)[0].strip() or text[:80]
            results.append({"title": title, "url": url, "snippet": text})

    for topic in data.get("RelatedTopics") or []:
        if "Topics" in topic:
            for sub in topic.get("Topics") or []:
                _append_topic(sub)
        else:
            _append_topic(topic)
        if len(results) >= limit:
            break
    return results[:limit]


class _TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self._parts: list[str] = []
        self._skip = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in ("script", "style", "noscript"):
            self._skip = True

    def handle_endtag(self, tag: str) -> None:
        if tag in ("script", "style", "noscript"):
            self._skip = False
        elif tag in ("p", "div", "br", "li", "h1", "h2", "h3", "h4", "tr") and not self._skip:
            self._parts.append("\n")

    def handle_data(self, data: str) -> None:
        if not self._skip:
            self._parts.append(data)

    def text(self) -> str:
        raw = "".join(self._parts)
        raw = re.sub(r"[ \t]+", " ", raw)
        raw = re.sub(r"\n{3,}", "\n\n", raw)
        return raw.strip()


def fetch_page(url: str, max_chars: int | None = None) -> str:
    """抓取网页并提取纯文本，长度截断。"""
    limit = max_chars if max_chars is not None else _page_max_chars()
    try:
        body = _request(url, timeout=_page_timeout())
    except requests.RequestException:
        return ""

    body = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", body)
    parser = _TextExtractor()
    try:
        parser.feed(body)
        text = parser.text()
    except Exception:
        text = re.sub(r"<[^>]+>", " ", body)
        text = re.sub(r"\s+", " ", text).strip()

    if len(text) > limit:
        return text[:limit] + "…"
    return text


def _fetch_pages_parallel(urls: list[str]) -> list[tuple[str, str]]:
    if not urls:
        return []
    workers = min(3, len(urls))
    contents: list[tuple[str, str]] = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(fetch_page, url): url for url in urls}
        for future in as_completed(futures):
            url = futures[future]
            try:
                text = future.result()
            except Exception:
                text = ""
            if text:
                contents.append((url, text))
    return contents


def enrich_results_with_content(
    results: list[dict],
    page_contents: list[tuple[str, str]],
) -> list[dict]:
    """将网页正文合并进搜索结果，供客户端展示来源摘要。"""
    by_url = {url: text for url, text in page_contents}
    enriched: list[dict] = []
    for item in results:
        row = dict(item)
        url = row.get("url") or ""
        if url in by_url:
            row["content"] = by_url[url]
        enriched.append(row)
    return enriched


def build_search_context(
    query: str,
    results: list[dict],
    page_contents: list[tuple[str, str]] | None = None,
) -> str:
    """将搜索结果格式化为可注入模型的上下文。"""
    if not results and not page_contents:
        return ""

    lines = [
        f"以下是与用户问题「{query}」相关的联网搜索结果。",
        "回答时请优先依据这些资料，引用外部来源时使用 Markdown 链接 [标题](url)。",
        "",
    ]
    page_by_url = {url: text for url, text in (page_contents or [])}
    for i, item in enumerate(results, 1):
        lines.append(f"[{i}] {item.get('title', '')}")
        lines.append(f"URL: {item.get('url', '')}")
        snippet = item.get("snippet") or ""
        if snippet:
            lines.append(f"摘要: {snippet}")
        page_text = item.get("content") or page_by_url.get(item.get("url") or "", "")
        if page_text:
            lines.append(f"正文摘录: {page_text}")
        lines.append("")

    extra_pages = [
        (url, text)
        for url, text in (page_contents or [])
        if url and text and not any(item.get("url") == url for item in results)
    ]
    if extra_pages:
        lines.append("--- 网页正文摘录 ---")
        for url, text in extra_pages:
            lines.append(f"来源: {url}")
            lines.append(text)
            lines.append("")

    return "\n".join(lines).strip()


def _urls_needing_fetch(results: list[dict]) -> list[str]:
    """从搜索结果中挑选需本地抓正文的 URL。"""
    min_chars = _min_content_chars()
    cap = _max_fetch_pages()
    urls: list[str] = []
    for item in results:
        if len(urls) >= cap:
            break
        url = (item.get("url") or "").strip()
        if not url:
            continue
        existing = (item.get("content") or "").strip()
        if len(existing) >= min_chars:
            continue
        urls.append(url)
    return urls


def _fetch_pages_for_results(results: list[dict]) -> list[tuple[str, str]]:
    """并行抓取搜索结果链接正文。"""
    if not _fetch_pages_enabled() or not results:
        return []
    urls = _urls_needing_fetch(results)
    if not urls:
        return []
    return _fetch_pages_parallel(urls)


def gather_search_context(
    query: str,
    *,
    max_results: int | None = None,
    sort: str | None = None,
    time_range: str | None = None,
) -> tuple[list[dict], str]:
    """执行搜索，并在本地抓取网页正文，返回 (results, context)。"""
    results = web_search(query, max_results=max_results, sort=sort, time_range=time_range)
    page_contents = _fetch_pages_for_results(results)
    enriched = enrich_results_with_content(results, page_contents)
    context = build_search_context(query, enriched, page_contents)
    return enriched, context
