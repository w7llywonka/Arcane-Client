(function () {
  "use strict";

  const menuButton = document.querySelector("[data-menu-button]");
  const menu = document.querySelector("[data-menu]");
  const copyButton = document.querySelector("[data-copy-command]");

  function closeMenu() {
    if (!menuButton || !menu) return;
    menuButton.setAttribute("aria-expanded", "false");
    menu.classList.remove("is-open");
  }

  if (menuButton && menu) {
    menuButton.addEventListener("click", function () {
      const open = menuButton.getAttribute("aria-expanded") === "true";
      menuButton.setAttribute("aria-expanded", String(!open));
      menu.classList.toggle("is-open", !open);
    });

    menu.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", closeMenu);
    });

    window.addEventListener("resize", function () {
      if (window.innerWidth > 680) closeMenu();
    });
  }

  if (copyButton) {
    copyButton.addEventListener("click", async function () {
      const command = ".\\gradlew.bat clean build";
      try {
        await navigator.clipboard.writeText(command);
      } catch (error) {
        const textarea = document.createElement("textarea");
        textarea.value = command;
        textarea.setAttribute("readonly", "");
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand("copy");
        textarea.remove();
      }

      copyButton.textContent = "Copied";
      window.setTimeout(function () {
        copyButton.textContent = "Copy";
      }, 1400);
    });
  }
}());
