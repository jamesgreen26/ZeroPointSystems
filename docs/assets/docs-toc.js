(() => {
  const root = document.querySelector("[data-toc-root]");
  const toc = document.querySelector("[data-toc]");
  const container = document.querySelector("[data-toc-container]");

  if (!root || !toc || !container) {
    return;
  }

  const headings = [...root.querySelectorAll("h2, h3")];
  if (!headings.length) {
    return;
  }

  const slugify = (text) =>
    text
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9\s-]/g, "")
      .replace(/\s+/g, "-");

  const usedIds = new Set(
    [...document.querySelectorAll("[id]")]
      .map((node) => node.id)
      .filter(Boolean),
  );

  headings.forEach((heading) => {
    if (!heading.id) {
      let id = slugify(heading.textContent);
      let suffix = 2;

      while (!id || usedIds.has(id)) {
        id = `${slugify(heading.textContent)}-${suffix}`;
        suffix += 1;
      }

      heading.id = id;
    }

    usedIds.add(heading.id);
  });

  const list = document.createElement("ul");
  list.className = "docs-toc-list";

  headings.forEach((heading) => {
    const item = document.createElement("li");
    item.className = `toc-level-${heading.tagName.slice(1)}`;

    const link = document.createElement("a");
    link.href = `#${heading.id}`;
    link.textContent = heading.textContent;

    item.appendChild(link);
    list.appendChild(item);
  });

  toc.appendChild(list);
  container.hidden = false;
})();
