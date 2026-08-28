const root = document.documentElement;
const navigation = document.querySelector(".site-nav");
const menuButton = document.querySelector(".menu-toggle");
const mobileMenu = document.querySelector(".mobile-menu");
const faqItems = Array.from(document.querySelectorAll(".faq-item"));

function updateNavigation() {
  if (!navigation) return;
  navigation.classList.toggle("scrolled", window.scrollY > 20);
}

function setMenu(open) {
  if (!menuButton || !mobileMenu || !navigation) return;

  menuButton.setAttribute("aria-expanded", String(open));
  menuButton.setAttribute("aria-label", open ? "Close menu" : "Open menu");
  mobileMenu.hidden = !open;
  navigation.classList.toggle("menu-active", open);
  document.body.classList.toggle("menu-open", open);
}

updateNavigation();
window.addEventListener("scroll", updateNavigation, { passive: true });

if (menuButton && mobileMenu) {
  menuButton.addEventListener("click", () => {
    setMenu(menuButton.getAttribute("aria-expanded") !== "true");
  });

  mobileMenu.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => setMenu(false));
  });

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") setMenu(false);
  });

  window.addEventListener("resize", () => {
    if (window.innerWidth >= 768) setMenu(false);
  });
}

const revealItems = Array.from(document.querySelectorAll(".reveal"));

if ("IntersectionObserver" in window) {
  const revealObserver = new IntersectionObserver(
    (entries, observer) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      });
    },
    {
      rootMargin: "0px 0px -8% 0px",
      threshold: 0.08
    }
  );

  revealItems.forEach((item) => revealObserver.observe(item));
} else {
  revealItems.forEach((item) => item.classList.add("is-visible"));
}

faqItems.forEach((item) => {
  item.addEventListener("toggle", () => {
    if (!item.open) return;
    faqItems.forEach((otherItem) => {
      if (otherItem !== item) otherItem.open = false;
    });
  });
});

const downloadDialog = document.querySelector("#download-dialog");
const downloadForm = document.querySelector(".download-form");
const downloadInput = document.querySelector("#download-code");
const downloadStatus = document.querySelector("#download-status");
const downloadButtons = Array.from(document.querySelectorAll("[data-download-open]"));
const downloadClose = document.querySelector("[data-download-close]");
const expectedDownloadCodeHash = "eb374395dbca9ae038c691f18bedddd16fd748d106aa2e424c6987b5ed4b7348";
const releaseDownloadUrl = "https://github.com/w7llywonka/Arcane-Client/releases/download/v2.3.6/Arcane-Client-2.3.6%2Bmc1.21.11.jar";

function resetDownloadForm() {
  if (!downloadForm || !downloadInput || !downloadStatus) return;
  downloadForm.reset();
  downloadInput.removeAttribute("aria-invalid");
  downloadStatus.textContent = "";
  delete downloadStatus.dataset.state;
}

function openDownloadDialog() {
  if (!downloadDialog || !downloadInput) return;
  setMenu(false);
  resetDownloadForm();
  document.body.classList.add("download-open");
  if (typeof downloadDialog.showModal === "function") {
    downloadDialog.showModal();
  } else {
    downloadDialog.setAttribute("open", "");
  }
  window.requestAnimationFrame(() => downloadInput.focus());
}

function closeDownloadDialog() {
  if (!downloadDialog) return;
  if (typeof downloadDialog.close === "function" && downloadDialog.open) {
    downloadDialog.close();
  } else {
    downloadDialog.removeAttribute("open");
  }
  document.body.classList.remove("download-open");
  resetDownloadForm();
}

async function hashDownloadCode(value) {
  const bytes = new TextEncoder().encode(value);
  const digest = await window.crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

downloadButtons.forEach((button) => button.addEventListener("click", openDownloadDialog));

if (downloadClose) {
  downloadClose.addEventListener("click", closeDownloadDialog);
}

if (downloadDialog) {
  downloadDialog.addEventListener("cancel", () => {
    document.body.classList.remove("download-open");
    resetDownloadForm();
  });

  downloadDialog.addEventListener("click", (event) => {
    if (event.target === downloadDialog) closeDownloadDialog();
  });
}

if (downloadForm && downloadInput && downloadStatus) {
  downloadForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submitButton = downloadForm.querySelector('button[type="submit"]');
    const value = downloadInput.value.trim();

    if (!value) {
      downloadInput.setAttribute("aria-invalid", "true");
      downloadStatus.textContent = "Enter your access code.";
      downloadInput.focus();
      return;
    }

    submitButton.disabled = true;
    downloadInput.removeAttribute("aria-invalid");
    downloadStatus.textContent = "Checking code…";

    try {
      const codeHash = await hashDownloadCode(value);
      if (codeHash !== expectedDownloadCodeHash) {
        downloadInput.setAttribute("aria-invalid", "true");
        downloadStatus.textContent = "That code is not valid.";
        downloadInput.select();
        return;
      }

      downloadStatus.dataset.state = "success";
      downloadStatus.textContent = "Code accepted. Starting download…";
      window.setTimeout(() => window.location.assign(releaseDownloadUrl), 180);
    } catch (error) {
      downloadStatus.textContent = "Your browser could not verify the code. Try a current browser.";
    } finally {
      submitButton.disabled = false;
    }
  });
}