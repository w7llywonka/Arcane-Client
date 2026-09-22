package dev.arcaneclient.additions.preview;

/** Explicitly labeled local mockups; never writes a server payment, role or statistic. */
public final class PreviewConfig {
    public boolean fakePay;
    public String payRecipient = "";
    public String payAmount = "100.00";
    public String payCurrency = "$";
    public boolean fakeRoles;
    public String role = "VIP";
    public int roleColor = 0xFFBA94FF;
    public boolean fakeStats;
    public String statsTitle = "STATS";
    public String firstLabel = "Balance";
    public String firstValue = "0";
    public String secondLabel = "Kills";
    public String secondValue = "0";
    public String thirdLabel = "Deaths";
    public String thirdValue = "0";
    public int statsMarginX = 8;
    public int statsMarginY = 150;
    public boolean statsOnLeft;

    public void sanitize() {
        payRecipient = player(payRecipient);
        payAmount = clean(payAmount, 18);
        payCurrency = clean(payCurrency, 8);
        role = clean(role, 24);
        roleColor |= 0xFF000000;
        statsTitle = clean(statsTitle, 32);
        firstLabel = clean(firstLabel, 24);
        firstValue = clean(firstValue, 24);
        secondLabel = clean(secondLabel, 24);
        secondValue = clean(secondValue, 24);
        thirdLabel = clean(thirdLabel, 24);
        thirdValue = clean(thirdValue, 24);
        statsMarginX = Math.clamp(statsMarginX, 0, 400);
        statsMarginY = Math.clamp(statsMarginY, 0, 400);
    }

    public static String player(String value) {
        String name = clean(value, 16);
        return name.matches("[A-Za-z0-9_]{1,16}") ? name : "";
    }

    public static String clean(String value, int limit) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(Math.min(value.length(), limit));
        value.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT && c != 0x00A7)
            .limit(limit).forEach(result::appendCodePoint);
        return result.toString().strip();
    }
}
