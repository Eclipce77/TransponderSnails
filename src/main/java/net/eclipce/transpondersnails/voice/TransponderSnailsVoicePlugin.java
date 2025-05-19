package net.eclipce.transpondersnails.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;

public class TransponderSnailsVoicePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId(){
        return "transpondersnails";
    }

    public void initialize(VoicechatApi api) {
    }
}
