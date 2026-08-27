const replayButton = document.querySelector(".replay-hiss");

if (replayButton) {
  replayButton.addEventListener("click", () => {
    const currentIntro = document.querySelector(".hiss-startup");
    if (!currentIntro) return;

    document.documentElement.classList.remove("hiss-seen");
    const replayIntro = currentIntro.cloneNode(true);
    currentIntro.replaceWith(replayIntro);

    window.setTimeout(() => {
      document.documentElement.classList.add("hiss-seen");
    }, 1600);
  });
}
