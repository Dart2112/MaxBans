package net.lapismc.maxbans.banmanager;

import net.lapismc.maxbans.MaxBans;
import net.lapismc.maxbans.Msg;

public class IPBan extends Ban {
    public IPBan(final String ip, final String reason, final String banner, final long created) {
        super(ip, reason, banner, created);
    }

    public String getKickMessage() {
        return Msg.get("disconnection.you-are-ipbanned", new String[]{"reason", "banner", "appeal-message"}, new String[]{this.getReason(), this.getBanner(), MaxBans.instance.getBanManager().getAppealMessage()});
    }
}
