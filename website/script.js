const root = document.documentElement;
const navigation = document.querySelector(".site-nav");
const menuButton = document.querySelector(".menu-toggle");
const mobileMenu = document.querySelector(".mobile-menu");
const replayButton = document.querySelector(".replay-hiss");
const faqItems = Array.from(document.querySelectorAll(".faq-item"));
let hissTimer;

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

function finishHiss() {
  window.clearTimeout(hissTimer);
  root.classList.add("hiss-seen");
}

function bindHissIntro(intro) {
  if (!intro) return;

  intro.addEventListener(
    "animationend",
    (event) => {
      if (event.animationName === "hiss-cover") finishHiss();
    },
    { once: true }
  );

  hissTimer = window.setTimeout(finishHiss, 1700);
}

function replayHiss() {
  const currentIntro = document.querySelector(".hiss-startup");
  if (!currentIntro) return;

  window.clearTimeout(hissTimer);
  root.classList.remove("hiss-seen");

  const freshIntro = currentIntro.cloneNode(true);
  currentIntro.replaceWith(freshIntro);
  void freshIntro.offsetWidth;
  bindHissIntro(freshIntro);
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

if (!root.classList.contains("hiss-seen")) {
  bindHissIntro(document.querySelector(".hiss-startup"));
}

if (replayButton) {
  replayButton.addEventListener("click", replayHiss);
}