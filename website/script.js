(function () {
  "use strict";

  const patterns = {
    field: {
      name: "Organized field",
      reason: "Rows + synchronized crop stages",
      score: 68,
      growth: [8, 9, 10, 11, 15, 16, 17, 18, 22, 23, 24, 25],
      alert: [16, 17],
      harvest: [],
      saplings: []
    },
    harvest: {
      name: "Harvest cycle",
      reason: "Mature crop reset + repeated activity",
      score: 74,
      growth: [9, 10, 11, 16, 17, 18, 23, 24, 25],
      alert: [17, 24],
      harvest: [10, 16, 18, 23],
      saplings: []
    },
    saplings: {
      name: "Sapling layout",
      reason: "Repeated spacing + organized placement",
      score: 57,
      growth: [8, 10, 12, 22, 24, 26],
      alert: [17],
      harvest: [],
      saplings: [8, 10, 12, 15, 17, 19, 22, 24, 26]
    }
  };

  const header = document.querySelector("[data-header]");
  const nav = document.querySelector("[data-nav]");
  const navToggle = document.querySelector("[data-nav-toggle]");
  const grid = document.querySelector("[data-chunk-grid]");
  const patternName = document.querySelector("[data-pattern-name]");
  const patternReason = document.querySelector("[data-pattern-reason]");
  const score = document.querySelector("[data-score]");
  const scoreOrbit = document.querySelector(".score-orbit");
  const patternButtons = Array.from(document.querySelectorAll("[data-pattern]"));
  const themeButtons = Array.from(document.querySelectorAll("[data-theme-choice]"));
  const gui = document.querySelector("[data-gui]");
  const copyButton = document.querySelector("[data-copy-command]");

  function setHeaderState() {
    if (header) {
      header.classList.toggle("is-scrolled", window.scrollY > 24);
    }
  }

  function closeNavigation() {
    if (!nav || !navToggle) return;
    nav.classList.remove("is-open");
    navToggle.setAttribute("aria-expanded", "false");
    document.body.style.overflow = "";
  }

  if (navToggle && nav) {
    navToggle.addEventListener("click", function () {
      const open = navToggle.getAttribute("aria-expanded") === "true";
      navToggle.setAttribute("aria-expanded", String(!open));
      nav.classList.toggle("is-open", !open);
      document.body.style.overflow = open ? "" : "hidden";
    });

    nav.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", closeNavigation);
    });
  }

  function createGrid() {
    if (!grid) return;
    const fragment = document.createDocumentFragment();
    for (let index = 0; index < 35; index += 1) {
      const cell = document.createElement("span");
      cell.className = "chunk";
      cell.dataset.cell = String(index);
      fragment.appendChild(cell);
    }
    grid.replaceChildren(fragment);
  }

  function includes(list, value) {
    return list.indexOf(value) !== -1;
  }

  function setPattern(key) {
    const pattern = patterns[key];
    if (!pattern || !grid) return;

    grid.querySelectorAll(".chunk").forEach(function (cell, index) {
      cell.className = "chunk";
      if ((index * 7 + 3) % 11 === 0) cell.classList.add("is-faint");
      if (includes(pattern.growth, index)) cell.classList.add("is-growth");
      if (includes(pattern.harvest, index)) cell.classList.add("is-harvest");
      if (includes(pattern.saplings, index)) cell.classList.add("is-sapling");
      if (includes(pattern.alert, index)) cell.classList.add("is-alert");
    });

    if (patternName) patternName.textContent = pattern.name;
    if (patternReason) patternReason.textContent = pattern.reason;
    if (score) score.textContent = String(pattern.score);
    if (scoreOrbit) scoreOrbit.style.setProperty("--score", String(pattern.score));

    patternButtons.forEach(function (button) {
      const active = button.dataset.pattern === key;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
  }

  createGrid();
  setPattern("field");

  patternButtons.forEach(function (button) {
    button.addEventListener("click", function () {
      setPattern(button.dataset.pattern);
    });
  });

  themeButtons.forEach(function (button) {
    button.setAttribute("aria-pressed", String(button.classList.contains("is-active")));
    button.addEventListener("click", function () {
      const choice = button.dataset.themeChoice || "arcane";
      themeButtons.forEach(function (candidate) {
        const active = candidate === button;
        candidate.classList.toggle("is-active", active);
        candidate.setAttribute("aria-pressed", String(active));
      });
      if (gui) {
        gui.dataset.color = choice;
        const label = gui.querySelector(".gui-topbar > b");
        if (label) label.textContent = choice.toUpperCase();
      }
    });
  });

  if (copyButton) {
    copyButton.addEventListener("click", async function () {
      const command = ".\\gradlew.bat clean build";
      try {
        await navigator.clipboard.writeText(command);
      } catch (error) {
        const text = document.createElement("textarea");
        text.value = command;
        text.setAttribute("readonly", "");
        text.style.position = "fixed";
        text.style.opacity = "0";
        document.body.appendChild(text);
        text.select();
        document.execCommand("copy");
        text.remove();
      }
      copyButton.textContent = "COPIED";
      window.setTimeout(function () {
        copyButton.textContent = "COPY";
      }, 1500);
    });
  }

  const reveals = Array.from(document.querySelectorAll(".reveal"));
  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -45px" });

    reveals.forEach(function (element) {
      observer.observe(element);
    });
  } else {
    reveals.forEach(function (element) {
      element.classList.add("is-visible");
    });
  }

  window.addEventListener("scroll", setHeaderState, { passive: true });
  window.addEventListener("resize", function () {
    if (window.innerWidth > 820) closeNavigation();
  });
  setHeaderState();
}());
