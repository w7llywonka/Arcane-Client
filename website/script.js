(() => {
  "use strict";

  const dialog = document.getElementById("download-dialog");
  const form = document.getElementById("download-form");
  const input = document.getElementById("access-code");
  const status = document.getElementById("download-status");
  const file = document.getElementById("download-file");
  const description = document.getElementById("download-description");
  const versionInputs = [...form.querySelectorAll('input[name="minecraft-version"]')];
  const submit = form.querySelector('button[type="submit"]');
  let opener;
  let attempt = 0;
  let request;

  function reset() {
    attempt++;
    request?.abort();
    request = undefined;
    form.reset();
    form.hidden = false;
    file.hidden = true;
    file.removeAttribute("href");
    input.removeAttribute("aria-invalid");
    form.removeAttribute("aria-busy");
    status.textContent = "";
    delete status.dataset.success;
    submit.disabled = false;
    updateVersionCopy();
  }
  function selectedVersion() {
    return versionInputs.find((option) => option.checked)?.value || "1.21.11";
  }
  function updateVersionCopy() {
    const version = selectedVersion();
    description.textContent = "Enter your download code to get ARCLoader for Minecraft "
      + version + ". Arcane is included and loads on your first launch.";
  }
  versionInputs.forEach((option) => option.addEventListener("change", updateVersionCopy));
  const requestedVersion = new URL(window.location.href).searchParams.get("version");
  if (["1.21.11", "26.3"].includes(requestedVersion)) {
    const requestedOption = versionInputs.find((option) => option.value === requestedVersion);
    if (requestedOption) requestedOption.checked = true;
    updateVersionCopy();
  }
  document.querySelectorAll("[data-download-open]").forEach((button) => {
    button.addEventListener("click", (event) => {
      event.preventDefault();
      opener = button;
      reset();
      dialog.showModal();
      input.focus();
    });
  });
  document
    .querySelector("[data-download-close]")
    .addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (event) => {
    const rect = dialog.getBoundingClientRect();
    if (
      event.target === dialog &&
      (event.clientX < rect.left ||
        event.clientX > rect.right ||
        event.clientY < rect.top ||
        event.clientY > rect.bottom)
    )
      dialog.close();
  });
  dialog.addEventListener("close", () => {
    reset();
    opener?.focus();
  });
  if (["1.21.11", "26.3"].includes(requestedVersion)) {
    opener = document.querySelector("[data-download-open]");
    reset();
    const requestedOption = versionInputs.find((option) => option.value === requestedVersion);
    if (requestedOption) requestedOption.checked = true;
    updateVersionCopy();
    dialog.showModal();
    input.focus();
  }
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submit.disabled) return;
    const code = input.value.trim();
    if (!code || code.length > 256) {
      input.setAttribute("aria-invalid", "true");
      status.textContent = !code
        ? "Paste your one-time code from Discord."
        : "That code is too long. Paste only the code from Discord.";
      input.focus();
      return;
    }
    const currentAttempt = ++attempt;
    const controller = new AbortController();
    request = controller;
    const timeout = setTimeout(() => controller.abort(), 10000);
    submit.disabled = true;
    form.setAttribute("aria-busy", "true");
    input.removeAttribute("aria-invalid");
    status.textContent = "Checking your code…";
    try {
      const response = await fetch("/api/verify-code", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code, minecraftVersion: selectedVersion() }),
        cache: "no-store",
        signal: controller.signal,
      });
      const result = await response.json();
      if (currentAttempt !== attempt || !dialog.open) return;
      if (
        response.ok &&
        result.ok === true &&
        /^\/api\/download\?version=(?:1\.21\.11|26\.3)$/.test(result.downloadUrl)
      ) {
        form.hidden = true;
        input.value = "";
        status.dataset.success = "";
        status.textContent = "Code verified for Minecraft " + selectedVersion()
          + ". Install this single ARCLoader.jar file.";
        file.href = result.downloadUrl;
        file.hidden = false;
        file.focus();
      } else if (response.status === 503 && result.error === "NOT_CONFIGURED") {
        status.textContent = "One-time codes are temporarily unavailable. Please try again shortly.";
      } else if (response.status === 400 || response.status === 401) {
        input.setAttribute("aria-invalid", "true");
        status.textContent = "That code is invalid, expired, or already used. Get a new one with /code in Discord.";
        input.focus();
        input.select();
      } else if (response.status === 429 && result.error === "WEEKLY_LIMIT") {
        status.textContent = "This Discord account has already used its weekly download.";
      } else {
        status.textContent = "Code verification is temporarily unavailable. Please try again later.";
      }
    } catch {
      if (currentAttempt === attempt && dialog.open) {
        status.textContent = "Couldn't reach code verification. Please try again in a moment.";
      }
    } finally {
      clearTimeout(timeout);
      if (currentAttempt === attempt) {
        request = undefined;
        submit.disabled = false;
        form.removeAttribute("aria-busy");
      }
    }
  });
})();
