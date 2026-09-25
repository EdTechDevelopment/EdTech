from __future__ import annotations

import html
import json
import re
import shutil
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "content"


class LinkParser(HTMLParser):
    def __init__(self, required_class: str):
        super().__init__()
        self.required_class = required_class
        self.links: list[tuple[str, str]] = []
        self.current_href: str | None = None
        self.current_text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() != "a":
            return
        attributes = dict(attrs)
        classes = (attributes.get("class") or "").split()
        if self.required_class in classes and attributes.get("href"):
            self.current_href = attributes["href"]
            self.current_text = []

    def handle_data(self, data: str) -> None:
        if self.current_href is not None:
            self.current_text.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() == "a" and self.current_href is not None:
            label = " ".join("".join(self.current_text).split())
            self.links.append((self.current_href, label))
            self.current_href = None
            self.current_text = []


class DescriptionParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.capturing = False
        self.done = False
        self.current: list[str] = []
        self.paragraphs: list[str] = []

    def flush(self) -> None:
        value = " ".join("".join(self.current).replace("\xa0", " ").split())
        if value:
            self.paragraphs.append(value)
        self.current = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        classes = (attributes.get("class") or "").split()
        if not self.done and tag.lower() == "span" and "Documentation" in classes:
            self.capturing = True
        elif self.capturing and tag.lower() in {"p", "li"}:
            self.flush()
        elif self.capturing and tag.lower() == "br":
            self.current.append(" ")

    def handle_data(self, data: str) -> None:
        if self.capturing:
            self.current.append(data)

    def handle_endtag(self, tag: str) -> None:
        if self.capturing and tag.lower() in {"p", "li"}:
            self.flush()
        elif self.capturing and tag.lower() == "span":
            self.flush()
            self.capturing = False
            self.done = True

    def value(self) -> str:
        return "\n\n".join(self.paragraphs)


def read_links(path: Path, css_class: str) -> list[tuple[str, str]]:
    parser = LinkParser(css_class)
    parser.feed(path.read_text(encoding="utf-8", errors="replace"))
    return parser.links


def read_description(relative_href: str | None) -> str:
    if not relative_href:
        return ""
    path = ROOT / relative_href
    if not path.exists():
        return ""
    parser = DescriptionParser()
    parser.feed(path.read_text(encoding="utf-8", errors="replace"))
    return parser.value()


def package_page(menu_href: str) -> str | None:
    raw_id = menu_href.removesuffix("_c_menu.html")
    safe_id = re.sub(r"[^A-Za-z0-9_]", "_", raw_id)
    candidate = CONTENT / f"Package_{safe_id}.html"
    return f"content/{candidate.name}" if candidate.exists() else None


def package_filename(full_name: str) -> str:
    safe_name = re.sub(r"[^A-Za-z0-9_.-]", "_", full_name).replace(".", "_")
    return f"package_{safe_name}.html"


def element_kind(href: str) -> str:
    prefix = href.split("_", 1)[0]
    labels = {
        "Class": "Класс",
        "Interface": "Интерфейс",
        "Enumeration": "Enum",
        "DataType": "Тип данных",
        "Package": "Пакет",
    }
    return labels.get(prefix, prefix)


def diagram_kind(href: str) -> str:
    prefix = href.split("_", 1)[0]
    labels = {
        "ClassDiagram": "Диаграммы классов",
        "PackageDiagram": "Диаграммы пакетов",
        "ComponentDiagram": "Диаграммы компонентов",
    }
    return labels.get(prefix, prefix)


def build_model() -> dict:
    package_links = [
        (href, name)
        for href, name in read_links(CONTENT / "class_navigator.html", "CategoryItemLink")
        if href.endswith("_c_menu.html") and not name.lower().startswith("show all")
    ]

    package_records: dict[str, dict] = {}
    for menu_href, full_name in package_links:
        menu_path = CONTENT / menu_href
        elements = []
        if menu_path.exists():
            for href, name in read_links(menu_path, "ItemLink"):
                if href.startswith("Package_"):
                    continue
                elements.append(
                    {
                        "name": name,
                        "kind": element_kind(href),
                        "href": f"content/{href}",
                        "description": read_description(f"content/{href}"),
                    }
                )
        original_href = package_page(menu_href)
        package_records[full_name] = {
            "name": full_name.rsplit(".", 1)[-1],
            "fullName": full_name,
            "href": f"readable/{package_filename(full_name)}",
            "originalHref": original_href,
            "description": read_description(original_href),
            "elements": sorted(elements, key=lambda item: item["name"].lower()),
            "children": {},
        }

    roots: dict[str, dict] = {}
    for full_name in sorted(package_records, key=lambda value: (value.count("."), value)):
        record = package_records[full_name]
        parent_name = full_name.rsplit(".", 1)[0] if "." in full_name else None
        if parent_name and parent_name in package_records:
            package_records[parent_name]["children"][record["name"]] = record
        else:
            roots[record["name"]] = record

    write_package_pages(package_records)

    def normalize(node: dict) -> dict:
        node["children"] = [
            normalize(child)
            for _, child in sorted(node["children"].items(), key=lambda pair: pair[0].lower())
        ]
        return node

    diagrams = []
    all_diagrams = CONTENT / "all_diagrams_menu.html"
    if all_diagrams.exists():
        for href, name in read_links(all_diagrams, "ItemLink"):
            diagrams.append(
                {
                    "name": name,
                    "kind": diagram_kind(href),
                    "href": f"content/{href}",
                }
            )

    return {
        "packages": [normalize(node) for _, node in sorted(roots.items())],
        "diagrams": sorted(diagrams, key=lambda item: (item["kind"], item["name"].lower())),
        "stats": {
            "packages": len(package_records),
            "elements": sum(len(record["elements"]) for record in package_records.values()),
            "diagrams": len(diagrams),
        },
    }


def display_path(full_name: str) -> str:
    return full_name if full_name == "identity" or full_name.startswith("identity.") else f"identity.{full_name}"


def paragraphs(value: str, empty_message: str) -> str:
    if not value:
        return f'<p class="empty-description">{html.escape(empty_message)}</p>'
    return "".join(f"<p>{html.escape(part)}</p>" for part in value.split("\n\n") if part)


def package_page_html(record: dict) -> str:
    children = sorted(record["children"].values(), key=lambda item: item["name"].lower())
    elements = record["elements"]

    if children:
        child_cards = "".join(
            f"""
            <a class="package-card" href="{html.escape(package_filename(child['fullName']))}">
              <div class="card-icon folder-icon">▰</div>
              <div>
                <div class="card-title">{html.escape(child['name'])}</div>
                <div class="card-path">{html.escape(display_path(child['fullName']))}</div>
                <div class="card-description">{html.escape(child['description'] or 'Описание пакета пока не заполнено.')}</div>
              </div>
              <span class="card-count">{len(child['children']) + len(child['elements'])}</span>
            </a>
            """
            for child in children
        )
    else:
        child_cards = '<div class="empty-state">Вложенных пакетов нет</div>'

    if elements:
        element_cards = "".join(
            f"""
            <a class="element-card" href="../{html.escape(item['href'])}">
              <div class="card-icon element-icon">◇</div>
              <div>
                <div class="card-title">{html.escape(item['name'])}</div>
                <div class="type-badge">{html.escape(item['kind'])}</div>
                <div class="card-description">{html.escape(item['description'] or 'Описание элемента пока не заполнено.')}</div>
              </div>
              <span class="open-arrow">›</span>
            </a>
            """
            for item in elements
        )
    else:
        element_cards = '<div class="empty-state">Классов и интерфейсов непосредственно в этом пакете нет</div>'

    original_link = (
        f'<a class="secondary" href="../{html.escape(record["originalHref"])}">Исходная спецификация VP</a>'
        if record["originalHref"]
        else ""
    )

    return f"""<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{html.escape(display_path(record['fullName']))}</title>
  <link rel="stylesheet" href="package.css">
</head>
<body>
  <main class="page">
    <nav class="breadcrumb"><span>Identity</span><span>›</span><span>{html.escape(record['fullName'].replace('.', ' › '))}</span></nav>
    <header class="package-header">
      <div>
        <div class="eyebrow">PACKAGE</div>
        <h1>{html.escape(record['name'])}</h1>
        <code>{html.escape(display_path(record['fullName']))}</code>
      </div>
      {original_link}
    </header>

    <section class="summary-grid">
      <article class="description-panel">
        <h2>Назначение пакета</h2>
        <div class="description">{paragraphs(record['description'], 'Описание пакета пока не заполнено в Visual Paradigm.')}</div>
      </article>
      <aside class="stats-panel">
        <div><strong>{len(children)}</strong><span>дочерних пакетов</span></div>
        <div><strong>{len(elements)}</strong><span>элементов</span></div>
      </aside>
    </section>

    <section class="content-section">
      <div class="section-heading"><h2>Дочерние пакеты</h2><span>{len(children)}</span></div>
      <div class="card-grid">{child_cards}</div>
    </section>

    <section class="content-section">
      <div class="section-heading"><h2>Классы и интерфейсы</h2><span>{len(elements)}</span></div>
      <div class="element-list">{element_cards}</div>
    </section>
  </main>
</body>
</html>
"""


def write_package_pages(package_records: dict[str, dict]) -> None:
    readable = ROOT / "readable"
    readable.mkdir(exist_ok=True)
    css = """
:root {
  color-scheme: light;
  --text: #172033;
  --muted: #667085;
  --line: #dde4ee;
  --accent: #3157d5;
  --soft: #edf2ff;
  --surface: #ffffff;
  --bg: #f6f8fc;
}
* { box-sizing: border-box; }
html { background: var(--bg); }
body { margin: 0; color: var(--text); background: var(--bg); font-family: "Segoe UI", Arial, sans-serif; line-height: 1.55; }
.page { max-width: 1180px; margin: 0 auto; padding: 32px 34px 80px; }
.breadcrumb { display: flex; gap: 8px; align-items: center; color: var(--muted); font-size: 13px; margin-bottom: 20px; }
.package-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.eyebrow { color: var(--accent); font-size: 12px; font-weight: 750; letter-spacing: .14em; }
h1 { margin: 3px 0 4px; font-size: clamp(30px, 5vw, 46px); line-height: 1.1; letter-spacing: -.025em; }
code { display: inline-block; color: #526078; background: #e9edf4; border-radius: 7px; padding: 5px 8px; font-size: 13px; }
.secondary { color: var(--accent); text-decoration: none; border: 1px solid #cad5f6; background: white; border-radius: 9px; padding: 9px 12px; white-space: nowrap; font-size: 13px; }
.secondary:hover { background: var(--soft); }
.summary-grid { display: grid; grid-template-columns: minmax(0, 1fr) 220px; gap: 16px; align-items: stretch; }
.description-panel, .stats-panel, .package-card, .element-card { background: var(--surface); border: 1px solid var(--line); box-shadow: 0 5px 18px rgba(15, 23, 42, .045); }
.description-panel { border-radius: 14px; padding: 22px 24px; }
.description-panel h2, .content-section h2 { margin: 0; font-size: 18px; }
.description { color: #3d4a5f; margin-top: 12px; }
.description p { margin: 0 0 11px; }
.description p:last-child { margin-bottom: 0; }
.empty-description { color: #8b95a7; font-style: italic; }
.stats-panel { display: grid; grid-template-columns: 1fr 1fr; border-radius: 14px; overflow: hidden; }
.stats-panel div { display: flex; flex-direction: column; justify-content: center; align-items: center; padding: 20px 10px; text-align: center; }
.stats-panel div + div { border-left: 1px solid var(--line); }
.stats-panel strong { color: var(--accent); font-size: 28px; }
.stats-panel span { color: var(--muted); font-size: 12px; }
.content-section { margin-top: 30px; }
.section-heading { display: flex; gap: 9px; align-items: center; margin-bottom: 12px; }
.section-heading span { color: var(--muted); background: #e9edf4; border-radius: 999px; min-width: 24px; padding: 2px 7px; text-align: center; font-size: 12px; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 10px; }
.package-card, .element-card { position: relative; display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; gap: 11px; color: inherit; text-decoration: none; border-radius: 12px; padding: 15px; transition: border-color .12s, transform .12s, box-shadow .12s; }
.package-card:hover, .element-card:hover { border-color: #9fb0eb; box-shadow: 0 9px 24px rgba(49, 87, 213, .09); transform: translateY(-1px); }
.card-icon { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 9px; font-weight: 700; }
.folder-icon { color: #a66c00; background: #fff3cf; }
.element-icon { color: var(--accent); background: var(--soft); }
.card-title { font-weight: 670; overflow-wrap: anywhere; }
.card-path { margin-top: 1px; color: var(--muted); font-family: Consolas, monospace; font-size: 11px; overflow-wrap: anywhere; }
.card-description { margin-top: 7px; color: #5a6679; font-size: 13px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.card-count { color: var(--muted); background: #eef1f6; border-radius: 999px; height: fit-content; padding: 2px 7px; font-size: 11px; }
.element-list { display: grid; gap: 8px; }
.type-badge { display: inline-block; margin-top: 3px; color: #3157d5; background: var(--soft); border-radius: 5px; padding: 2px 6px; font-size: 11px; }
.open-arrow { align-self: center; color: #94a0b3; font-size: 25px; }
.empty-state { color: var(--muted); background: #edf0f5; border: 1px dashed #cfd7e4; border-radius: 11px; padding: 18px; text-align: center; font-size: 13px; }
@media (max-width: 720px) {
  .page { padding: 24px 18px 60px; }
  .package-header { flex-direction: column; }
  .summary-grid { grid-template-columns: 1fr; }
  .card-grid { grid-template-columns: 1fr; }
}
""".strip()
    (readable / "package.css").write_text(css + "\n", encoding="utf-8")
    for record in package_records.values():
        (readable / package_filename(record["fullName"])).write_text(
            package_page_html(record), encoding="utf-8"
        )


def build_html(model: dict) -> str:
    data = json.dumps(model, ensure_ascii=False).replace("</", "<\\/")
    return f"""<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Identity Architecture</title>
  <style>
    :root {{
           color-scheme: light;
      --bg: #f4f7fb;
      --panel: #ffffff;
      --text: #172033;
      --muted: #667085;
      --line: #dfe5ee;
      --accent: #3157d5;
      --accent-soft: #edf2ff;
      --hover: #f3f6fb;
      --header: #111827;
      --shadow: 0 8px 24px rgba(15, 23, 42, .08);
    }}
    * {{ box-sizing: border-box; }}
    html, body {{ height: 100%; margin: 0; }}
    body {{
      display: grid;
      grid-template-rows: 64px 1fr;
      overflow: hidden;
      background: var(--bg);
      color: var(--text);
      font-family: "Segoe UI", Arial, sans-serif;
    }}
    header {{
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 20px;
      padding: 0 22px;
      background: var(--header);
      color: white;
      box-shadow: var(--shadow);
      z-index: 4;
    }}
    .brand {{ display: flex; align-items: baseline; gap: 12px; min-width: 0; }}
    .brand strong {{ font-size: 18px; letter-spacing: .2px; white-space: nowrap; }}
    .brand span {{ color: #aeb9cc; font-size: 13px; white-space: nowrap; }}
    .header-actions {{ display: flex; gap: 8px; }}
    .header-actions a {{
      color: #dce5ff;
      text-decoration: none;
      border: 1px solid #3b465a;
      border-radius: 8px;
      padding: 8px 11px;
      font-size: 13px;
    }}
    .header-actions a:hover {{ background: #202b3f; color: white; }}
    main {{ display: grid; grid-template-columns: 390px minmax(0, 1fr); min-height: 0; }}
    aside {{
      display: flex;
      flex-direction: column;
      min-width: 0;
      background: var(--panel);
      border-right: 1px solid var(--line);
    }}
    .sidebar-top {{ padding: 16px 16px 10px; border-bottom: 1px solid var(--line); }}
    .search {{
      width: 100%;
      border: 1px solid #cfd7e4;
      border-radius: 10px;
      padding: 10px 12px;
      outline: none;
      font: inherit;
      font-size: 14px;
    }}
    .search:focus {{ border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }}
    .tabs {{ display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 10px; }}
    .tab {{
      border: 0;
      border-radius: 8px;
      padding: 9px;
      background: #eef1f6;
      color: #465166;
      cursor: pointer;
      font-weight: 600;
    }}
    .tab.active {{ background: var(--accent); color: white; }}
    .tree-actions {{ display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }}
    .tree-actions span {{ color: var(--muted); font-size: 12px; }}
    .tree-actions div {{ display: flex; gap: 6px; }}
    .small-button {{
      border: 1px solid var(--line);
      border-radius: 7px;
      background: white;
      padding: 5px 8px;
      color: #4a5568;
      cursor: pointer;
      font-size: 12px;
    }}
    .small-button:hover {{ background: var(--hover); }}
    #tree {{ overflow: auto; padding: 10px 10px 30px; flex: 1; }}
    details {{ margin: 2px 0; }}
    details details {{ margin-left: 17px; }}
    summary {{
      display: flex;
      align-items: center;
      gap: 7px;
      min-height: 34px;
      padding: 5px 7px;
      border-radius: 8px;
      cursor: pointer;
      list-style: none;
      user-select: none;
    }}
    summary::-webkit-details-marker {{ display: none; }}
    summary:hover {{ background: var(--hover); }}
    summary::before {{ content: "▸"; width: 13px; color: #7b8798; transition: transform .12s; }}
    details[open] > summary::before {{ transform: rotate(90deg); }}
    .folder {{ color: #d4971f; font-size: 16px; }}
    .node-label {{ overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }}
    .count {{ margin-left: auto; color: #8b95a7; font-size: 11px; }}
    .elements {{ margin: 2px 0 7px 36px; display: grid; gap: 1px; }}
    .element, .diagram {{
      display: grid;
      grid-template-columns: 18px minmax(0, 1fr);
      gap: 7px;
      width: 100%;
      border: 0;
      border-radius: 7px;
      background: transparent;
      padding: 7px 8px;
      color: #334155;
      text-align: left;
      cursor: pointer;
      font: inherit;
      font-size: 13px;
    }}
    .element:hover, .diagram:hover, .element.active, .diagram.active {{ background: var(--accent-soft); color: #203da2; }}
    .type {{ color: #8290a4; font-size: 11px; grid-column: 2; margin-top: -5px; }}
    .diagram-groups details {{ margin-left: 0; }}
    .diagram-groups .elements {{ margin-left: 21px; }}
    .empty {{ padding: 24px 14px; color: var(--muted); text-align: center; font-size: 13px; }}
    .viewer {{ display: grid; grid-template-rows: 48px 1fr; min-width: 0; min-height: 0; padding: 12px; gap: 0; }}
    .viewer-toolbar {{
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-width: 0;
      padding: 0 13px;
      background: var(--panel);
      border: 1px solid var(--line);
      border-bottom: 0;
      border-radius: 12px 12px 0 0;
    }}
    #currentTitle {{ overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; font-size: 14px; }}
    #openPage {{ color: var(--accent); text-decoration: none; font-size: 13px; white-space: nowrap; margin-left: 15px; }}
    iframe {{ width: 100%; height: 100%; border: 1px solid var(--line); border-radius: 0 0 12px 12px; background: white; box-shadow: var(--shadow); }}
    @media (max-width: 850px) {{
      body {{ grid-template-rows: auto 1fr; overflow: auto; }}
      header {{ min-height: 64px; flex-wrap: wrap; padding: 12px 16px; }}
      .brand span {{ display: none; }}
      main {{ grid-template-columns: 1fr; grid-template-rows: 48vh 70vh; }}
      aside {{ border-right: 0; border-bottom: 1px solid var(--line); }}
      .viewer {{ padding: 8px; }}
    }}
  </style>
</head>
<body>
  <header>
    <div class="brand">
      <strong>Identity Architecture</strong>
      <span>интерактивный каталог Visual Paradigm</span>
    </div>
    <nav class="header-actions">
      <a href="visual-paradigm-report.html" target="_blank">Исходный отчёт VP</a>
      <a href="README.md" target="_blank">README</a>
    </nav>
  </header>

  <main>
    <aside>
      <div class="sidebar-top">
        <input id="search" class="search" type="search" placeholder="Поиск пакета, класса или диаграммы…" autocomplete="off">
        <div class="tabs">
          <button class="tab active" data-tab="packages">Пакеты</button>
          <button class="tab" data-tab="diagrams">Диаграммы</button>
        </div>
        <div class="tree-actions">
          <span id="stats"></span>
          <div>
            <button id="expandAll" class="small-button">Раскрыть всё</button>
            <button id="collapseAll" class="small-button">Свернуть</button>
          </div>
        </div>
      </div>
      <div id="tree"></div>
    </aside>

    <section class="viewer">
      <div class="viewer-toolbar">
        <div id="currentTitle">Общая информация о проекте</div>
        <a id="openPage" href="content/project_content.html" target="_blank">Открыть отдельно ↗</a>
      </div>
      <iframe id="contentFrame" name="contentFrame" src="content/project_content.html" title="Описание элемента"></iframe>
    </section>
  </main>

  <script>
    const MODEL = {data};
    let activeTab = "packages";

    const tree = document.getElementById("tree");
    const search = document.getElementById("search");
    const frame = document.getElementById("contentFrame");
    const title = document.getElementById("currentTitle");
    const openPage = document.getElementById("openPage");
    const stats = document.getElementById("stats");

    function textMatches(value, query) {{
      return (value || "").toLocaleLowerCase("ru").includes(query);
    }}

    function packageMatches(node, query) {{
      if (!query) return true;
      return textMatches(node.fullName, query)
        || node.elements.some(item => textMatches(item.name, query) || textMatches(item.kind, query))
        || node.children.some(child => packageMatches(child, query));
    }}

    function openContent(href, label, button) {{
      if (!href) return;
      frame.src = href;
      title.textContent = label;
      openPage.href = href;
      document.querySelectorAll(".element.active, .diagram.active").forEach(item => item.classList.remove("active"));
      if (button) button.classList.add("active");
    }}

    function makeElement(item, query) {{
      if (query && !textMatches(item.name, query) && !textMatches(item.kind, query)) return null;
      const button = document.createElement("button");
      button.className = "element";
      button.title = item.name;
      button.innerHTML = `<span>◇</span><span>${{escapeHtml(item.name)}}</span><span class="type">${{escapeHtml(item.kind)}}</span>`;
      button.addEventListener("click", () => openContent(item.href, item.name, button));
      return button;
    }}

    function renderPackage(node, query, depth = 0) {{
      if (!packageMatches(node, query)) return null;
      const details = document.createElement("details");
      details.open = Boolean(query) || depth === 0;

      const summary = document.createElement("summary");
      summary.title = node.fullName;
      const total = node.elements.length + node.children.length;
      summary.innerHTML = `<span class="folder">▰</span><span class="node-label">${{escapeHtml(node.name)}}</span><span class="count">${{total}}</span>`;
      summary.addEventListener("click", () => {{
        if (node.href) openContent(node.href, `Пакет ${{node.fullName}}`);
      }});
      details.appendChild(summary);

      const elements = document.createElement("div");
      elements.className = "elements";
      node.elements.forEach(item => {{
        const child = makeElement(item, query);
        if (child) elements.appendChild(child);
      }});
      if (elements.childElementCount) details.appendChild(elements);

      node.children.forEach(childNode => {{
        const child = renderPackage(childNode, query, depth + 1);
        if (child) details.appendChild(child);
      }});
      return details;
    }}

    function renderPackages(query) {{
      MODEL.packages.forEach(node => {{
        const packageNode = renderPackage(node, query);
        if (packageNode) tree.appendChild(packageNode);
      }});
    }}

    function renderDiagrams(query) {{
      const groups = new Map();
      MODEL.diagrams.forEach(item => {{
        if (query && !textMatches(item.name, query) && !textMatches(item.kind, query)) return;
        if (!groups.has(item.kind)) groups.set(item.kind, []);
        groups.get(item.kind).push(item);
      }});

      const wrapper = document.createElement("div");
      wrapper.className = "diagram-groups";
      groups.forEach((items, groupName) => {{
        const details = document.createElement("details");
        details.open = Boolean(query) || groups.size <= 3;
        const summary = document.createElement("summary");
        summary.innerHTML = `<span class="folder">▧</span><span class="node-label">${{escapeHtml(groupName)}}</span><span class="count">${{items.length}}</span>`;
        details.appendChild(summary);
        const elements = document.createElement("div");
        elements.className = "elements";
        items.forEach(item => {{
          const button = document.createElement("button");
          button.className = "diagram";
          button.innerHTML = `<span>▱</span><span>${{escapeHtml(item.name)}}</span>`;
          button.addEventListener("click", () => openContent(item.href, item.name, button));
          elements.appendChild(button);
        }});
        details.appendChild(elements);
        wrapper.appendChild(details);
      }});
      tree.appendChild(wrapper);
    }}

    function escapeHtml(value) {{
      return String(value).replace(/[&<>'"]/g, char => ({{
        "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
      }})[char]);
    }}

    function render() {{
      const query = search.value.trim().toLocaleLowerCase("ru");
      tree.replaceChildren();
      if (activeTab === "packages") {{
        renderPackages(query);
        stats.textContent = `${{MODEL.stats.packages}} пакетов · ${{MODEL.stats.elements}} элементов`;
      }} else {{
        renderDiagrams(query);
        stats.textContent = `${{MODEL.stats.diagrams}} диаграмм`;
      }}
      if (!tree.childElementCount) tree.innerHTML = '<div class="empty">Ничего не найдено</div>';
    }}

    document.querySelectorAll(".tab").forEach(button => {{
      button.addEventListener("click", () => {{
        document.querySelectorAll(".tab").forEach(item => item.classList.remove("active"));
        button.classList.add("active");
        activeTab = button.dataset.tab;
        search.value = "";
        render();
      }});
    }});

    search.addEventListener("input", render);
    document.getElementById("expandAll").addEventListener("click", () => {{
      tree.querySelectorAll("details").forEach(item => item.open = true);
    }});
    document.getElementById("collapseAll").addEventListener("click", () => {{
      tree.querySelectorAll("details").forEach(item => item.open = false);
    }});

    render();
  </script>
</body>
</html>
"""


def improve_report_css() -> None:
    css_path = CONTENT / "report.css"
    backup_path = CONTENT / "report.visual-paradigm.css"
    marker = "/* Identity readable overrides */"
    css = css_path.read_text(encoding="utf-8", errors="replace")
    if not backup_path.exists():
        shutil.copy2(css_path, backup_path)
    if marker in css:
        return
    overrides = """

/* Identity readable overrides */
html, body {
  background: #ffffff !important;
  color: #1f2937 !important;
  font-family: "Segoe UI", Arial, sans-serif !important;
  font-size: 14px !important;
  line-height: 1.5 !important;
}
a { color: #3157d5 !important; }
.PageTitle { color: #172033 !important; font-size: 26px !important; font-weight: 650 !important; }
.HeaderText { color: #667085 !important; font-size: 13px !important; }
.Category { color: #172033 !important; font-size: 18px !important; font-weight: 650 !important; padding-top: 18px !important; }
.TableHeader { background: #edf2ff !important; }
.TableHeaderText { color: #263b84 !important; font-weight: 650 !important; }
.TableRow1 { background: #ffffff !important; }
.TableRow2 { background: #f8fafc !important; }
.TableContent, .TableContentDocumentation { color: #334155 !important; padding-top: 5px !important; padding-bottom: 5px !important; }
.PageParentTitle { color: #5b6b83 !important; }
"""
    css_path.write_text(css + overrides, encoding="utf-8")


def main() -> None:
    required = [CONTENT / "class_navigator.html", CONTENT / "all_diagrams_menu.html"]
    missing = [str(path) for path in required if not path.exists()]
    if missing:
        raise SystemExit("Не найден экспорт Project Publisher: " + ", ".join(missing))

    source_index = ROOT / "index.html"
    original_index = ROOT / "visual-paradigm-report.html"
    if source_index.exists() and not original_index.exists():
        shutil.copy2(source_index, original_index)

    model = build_model()
    source_index.write_text(build_html(model), encoding="utf-8")
    improve_report_css()

    print(f"Packages: {model['stats']['packages']}")
    print(f"Elements: {model['stats']['elements']}")
    print(f"Diagrams: {model['stats']['diagrams']}")
    print(f"Created: {source_index}")


if __name__ == "__main__":
    main()
