"""判断用户是否在询问需要联网的外部信息（非寒暄、非本地知识库问题）。"""

import re

_SMALL_TALK = frozenset({
    "你好", "您好", "嗨", "hi", "hello", "hey", "在吗", "在不在",
    "谢谢", "感谢", "多谢", "好的", "ok", "okay", "嗯", "哦", "再见", "拜拜",
})

_WEB_KEYWORDS = (
    "新闻", "资讯", "头条", "时事", "最新消息",
    "天气", "气温", "降雨", "预报",
    "股价", "股票", "汇率", "基金", "行情",
    "热搜", "世界杯", "赛事",
    "联网搜索", "搜索一下", "帮我搜",
    "news", "weather", "stock price", "search the web",
)

_EXPLICIT_PATTERNS = (
    re.compile(r"搜(索|一下).+"),
    re.compile(r"(最新|今天|现在|当前|近期).+(消息|新闻|动态|进展|情况|价格|股价)"),
)


def looks_like_web_query(text: str) -> bool:
    q = text.strip()
    if not q:
        return False
    lower = q.lower()
    if lower in _SMALL_TALK or q in _SMALL_TALK:
        return False
    if len(q) <= 6 and not any(kw in lower for kw in _WEB_KEYWORDS):
        return False
    if any(p.search(q) for p in _EXPLICIT_PATTERNS):
        return True
    return any(kw in lower for kw in _WEB_KEYWORDS)
