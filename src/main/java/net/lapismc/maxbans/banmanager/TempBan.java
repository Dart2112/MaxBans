package net.lapismc.maxbans.banmanager;

import net.lapismc.maxbans.MaxBans;
import net.lapismc.maxbans.Msg;
import net.lapismc.maxbans.util.Util;

public class TempBan extends Ban implements Temporary {
    private long expires;

    TempBan(final String user, final String reason, final String banner, final long created, final long expires) {
        super(user, reason, banner, created);
        this.expires = expires;
    }

    public long getExpires() {
        return this.expires;
    }

    public boolean hasExpired() {
        return System.currentTimeMillis() > this.expires;
    }

    public String getKickMessage() {
        return Msg.get("disconnection.you-are-temp-banned", new String[]{"reason", "banner", "time", "appeal-message"}, new String[]{this.getReason(), this.getBanner(), Util.getTimeUntil(this.expires), MaxBans.instance.getBanManager().getAppealMessage()});
    }
}
