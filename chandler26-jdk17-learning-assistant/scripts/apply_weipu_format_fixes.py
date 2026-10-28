# -*- coding: utf-8 -*-
"""
按维普《上海大学继续教育学院毕业论文（设计）模版（成教）》修正格式。

覆盖的错误（共 270 种中的绝大多数）：
  1. 页面设置-页眉（191 处）：补/修正页眉文本、仿宋、五号、右对齐
  2. 正文文本-行距（133+4 处）：固定值/单倍 -> 1.5 倍
  3. 正文二级标题-字号（50 处）：四号 -> 小三
  4. 正文二级标题-行距（44 处）：-> 1.5 倍
  5. 参考文献内容-字号（35 处）：小四 -> 五号
  6. 二级/一级标题-字体（10 处）：宋体 -> 黑体
  7. 附录标题（15 处）：写法 "附录A" -> "附    录A"，行距 1.5 倍
  8. 致谢标题写法："致 谢" -> "致    谢"
  9. 三级标题字号：小四 -> 四号
 10. 数字日期：2026年9月5日 -> 2026-09-05
"""
import copy
import re
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt

PATH = "/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant/docs/基于大模型的英语学习助手-钱丁君-毕业论文.docx"

NS_W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
HEADER_TEXT = "上海大学继续教育学院毕业论文(设计)"   # 维普规范原文（半角括号）
SKIP_LINE_SPACING_STYLES = {"Caption"}                 # 表题/图题保持原样


def set_line_spacing(p_el, line="360", rule="auto"):
    """写入 1.5 倍行距（w:line=360 + w:lineRule=auto），保留 before/after 等已有属性。"""
    pPr = p_el.find(NS_W + "pPr")
    if pPr is None:
        pPr = OxmlElement("w:pPr")
        p_el.insert(0, pPr)
    sp = pPr.find(NS_W + "spacing")
    if sp is None:
        sp = OxmlElement("w:spacing")
        pPr.append(sp)
    sp.set(qn("w:line"), line)
    sp.set(qn("w:lineRule"), rule)


def set_run_size(p_el, val):
    """给段落所有 run 写死字号（半磅值）。"""
    for r in p_el.findall(NS_W + "r"):
        rPr = r.find(NS_W + "rPr")
        if rPr is None:
            rPr = OxmlElement("w:rPr")
            r.insert(0, rPr)
        sz = rPr.find(NS_W + "sz")
        if sz is None:
            sz = OxmlElement("w:sz")
            rPr.append(sz)
        sz.set(qn("w:val"), str(val))
        szCs = rPr.find(NS_W + "szCs")
        if szCs is None:
            szCs = OxmlElement("w:szCs")
            rPr.append(szCs)
        szCs.set(qn("w:val"), str(val))


def force_east_asia_font(p_el, font="黑体"):
    """把 run 的中文字体强制改成指定字体（样式里的黑体会被显式宋体覆盖）。"""
    n = 0
    for r in p_el.findall(NS_W + "r"):
        rPr = r.find(NS_W + "rPr")
        if rPr is None:
            rPr = OxmlElement("w:rPr")
            r.insert(0, rPr)
        rf = rPr.find(NS_W + "rFonts")
        if rf is None:
            rf = OxmlElement("w:rFonts")
            rPr.append(rf)
        if rf.get(qn("w:eastAsia")) not in (None, font):
            rf.set(qn("w:eastAsia"), font)
            n += 1
    return n


def set_style_size(doc, style_name, val):
    rPr = doc.styles[style_name].element.find(NS_W + "rPr")
    if rPr is None:
        rPr = OxmlElement("w:rPr")
        doc.styles[style_name].element.append(rPr)
    for tag in ("w:sz", "w:szCs"):
        el = rPr.find(NS_W + tag[2:])
        if el is None:
            el = OxmlElement(tag)
            rPr.append(el)
        el.set(qn("w:val"), str(val))


def main():
    doc = Document(PATH)
    stats = {}

    # ---------- 1. 页眉 ----------
    hs = 0
    for sec in doc.sections:
        hp = sec.header.paragraphs[0]
        hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        for r in list(hp.runs):
            r._r.getparent().remove(r._r)
        run = hp.add_run(HEADER_TEXT)
        run.font.name = "FangSong"
        run.font.size = Pt(10.5)          # 五号
        run._element.rPr.rFonts.set(qn("w:eastAsia"), "仿宋")
        sec.header.is_linked_to_previous = False
        hs += 1
    stats["页眉设置"] = hs

    # ---------- 2. 行距 1.5 倍 ----------
    n = 0
    for p in doc.paragraphs:
        st = p.style.name or ""
        if st in SKIP_LINE_SPACING_STYLES or st.startswith("toc"):
            continue
        set_line_spacing(p._p)
        n += 1
    stats["1.5倍行距"] = n

    # ---------- 3. 标题字号 ----------
    set_style_size(doc, "Heading 2", 30)   # 小三 15pt
    set_style_size(doc, "Heading 3", 28)   # 四号 14pt
    stats["标题字号"] = "Heading2=小三(30), Heading3=四号(28)"

    # ---------- 4. 标题中文字体修正 ----------
    n = 0
    for p in doc.paragraphs:
        if (p.style.name or "").startswith("Heading"):
            n += force_east_asia_font(p._p, "黑体")
    stats["标题字体宋体->黑体"] = n

    # ---------- 5. 参考文献内容 五号 ----------
    start = end = None
    for i, p in enumerate(doc.paragraphs):
        if p.text.strip() == "参考文献" and (p.style.name or "").startswith("Heading"):
            start = i + 1
            break
    if start is not None:
        for j in range(start, len(doc.paragraphs)):
            p = doc.paragraphs[j]
            if (p.style.name or "").startswith("Heading"):
                end = j
                break
        end = end or len(doc.paragraphs)
        n = 0
        for j in range(start, end):
            if doc.paragraphs[j].text.strip():
                set_run_size(doc.paragraphs[j]._p, 21)   # 五号 10.5pt
                n += 1
        stats["参考文献五号"] = f"{n} 段 [{start}-{end-1}]"

    # ---------- 6. 附录 / 致谢 标题写法 ----------
    n = 0
    for p in doc.paragraphs:
        t = p.text.strip()
        if t.startswith("附录A") or t.startswith("附录B"):
            for r in p.runs:
                if r.text.strip().startswith("附录"):
                    r.text = r.text.replace("附录A", "附    录A").replace("附录B", "附    录B")
                    n += 1
                    break
        elif t == "致 谢" and (p.style.name or "").startswith("Heading"):
            for r in p.runs:
                if "致" in r.text:
                    r.text = r.text.replace("致 谢", "致    谢")
                    n += 1
                    break
    stats["附录/致谢标题写法"] = n

    # ---------- 7. 数字日期 ----------
    n = 0
    for p in doc.paragraphs:
        for r in p.runs:
            if "2026年9月5日" in r.text:
                r.text = r.text.replace("2026年9月5日", "2026-09-05")
                n += 1
    stats["日期格式"] = n

    doc.save(PATH)
    for k, v in stats.items():
        print(f"  {k}: {v}")
    print("done")


if __name__ == "__main__":
    sys.exit(main())
