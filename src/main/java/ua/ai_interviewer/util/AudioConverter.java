package ua.ai_interviewer.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;

import java.io.File;

@Slf4j
@Component
public class AudioConverter extends DefaultFFMPEGLocator {

    public File convertToMp3(File source, String targetName) {
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("libmp3lame");
        audio.setBitRate(128000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp3");
        attrs.setAudioAttributes(audio);

        Encoder encoder = new Encoder();
        File file = new File(targetName + ".mp3");
        try {
            encoder.encode(new MultimediaObject(source), file, attrs);
            log.trace("Successful converted and create new file {}", file.getAbsolutePath());
        } catch (Exception e) {
            log.error("An error has occurred while converting audio", e);
        }

        return file;
    }
}